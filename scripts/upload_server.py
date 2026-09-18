#!/usr/bin/env python3
"""Servidor de subida de capturas/imagenes (persistente en .toolchain/).

Uso: python3 upload_server.py <puerto> <directorio-de-uploads>

Sirve:
  GET  /                       -> formulario HTML para subir una imagen.
  POST /upload                -> multipart (campo "file") o body crudo (`?name=...`).
  GET  /files/<nombre>      -> descarga del archivo (Content-Length exacto).
  GET  /health              -> "ok" (para sondeo del watchdog/ingress).

Guarda en DIR_UPLOADS (por defecto .toolchain/uploads del repo), QUE SOBREVIVE a los
reinicios del sandbox (a diferencia de /tmp, que se vacia). El nombre se prefija con
un short-id para evitar colisiones.

No usa `cgi`(eliminado en Python 3.13): el multipart se parsea
a mano con el boundary del Content-Type.
"""
import email.parser
import mimetypes
import os
import re
import sys
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO

DIR_UPLOADS = "/workspace/project/PanalinkV2.0Ofiicial/.toolchain/uploads"
MAX_BODY = 64 * 1024 * 1024  # 64 MB
CHUNK = 64 * 1024
os.makedirs(DIR_UPLOADS, exist_ok=True)


def shortid() -> str:
    return "%x" % int(time.time() * 1000)


def safe_name(name: str) -> str:
    name = urllib.parse.unquote(name)
    name = os.path.basename(name.replace("\\", "/"))
    name = re.sub(r"[^A-Za-z0-9._+-]", "_", name)
    if not name or name in (".", ".."):
        return ""
    return name[-120:]


def parse_multipart(body: bytes, content_type: str):
    """Devuelve (nombre_de_archivo, contenido) del primer file-part, o (None, None)."""
    m = re.search(r"boundary=([^;]+)", content_type or "")
    if not m:
        return None, None
    boundary = m.group(1).strip().strip('"').encode("utf-8")
    delimiter = b"--" + boundary
    # Saltar el preambulo hast en el primer delimiter
    idx = body.find(delimiter)
    if idx == -1:
        return None, None
    idx += len(delimiter)
    # Linea CRLF despues del delimiter; encabezados hast un \r\n\r\n
    while True:
        if body[idx: idx+2] == b"\r\n":
            idx += 2
            break
        elif body[idx: idx+2] == b"--":   # ultimo delimiter
            return None, None
        idx = body.find(b"\r\n", idx)
        if idx == -1:
            return None, None
        idx += 2
    header_end = body.find(b"\r\n\r\n", idx)
    if header_end == -1:
        return None, None
    headers_raw = body[idx:header_end].decode("utf-8", "replace")
    idx = header_end + 4
    fname = None
    for line in headers_raw.split("\r\n"):
        lm = re.search(r'filename="([^"]*)"', line, re.I)
        if lm:
            fname = safe_name(lm.group(1))
    # fin del part: \r\n--boundary
    end = body.find(b"\r\n" + delimiter, idx)
    if end == -1:
        # Sin CRLF final (ultimo part sin trailing boundary)
        end = len(body)
    return fname, body[idx:end]


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "PanalinkUpload/1.0"

    def log_message(self, fmt, *args):
        sys.stderr.write("[%s] %s\n" % (self.log_date_time_string(), fmt % args))

    def _send(self, code, ctype, body: bytes, extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def _html(self, title, msg):
        body = ("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"utf-8\">"
                "<title>%s</title></head><body style=\"font-family:sans-serif;padding:2em\">"
                "%s</body></html>" % (title, msg)).encode("utf-8")
        return body

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        if path == "/health":
            self._send(200, "text/plain", b"ok")
            return
        if path == "/" or path == "/index.html":
            form = ("<h2>Sube una captura (imagen)</h2>"
                    "<form action=\"/upload\" method=\"post\" enctype=\"multipart/form-data\">"
                    "<input type=\"file\" name=\"file\" accept=\"image/*\"> "
                    "<button type=\"submit\">Enviar</button></form>"
                    "<p>Respuesta: te devuelve el enlace /files/&lt;nombre&gt;.</p>")
            self._send(200, "text/html; charset=utf-8", self._html("Subir captura", form))
            return
        if path.startswith("/files/"):
            name = safe_name(path[len("/files/"):])
            fp = os.path.join(DIR_UPLOADS, name)
            if not name or not os.path.isfile(fp):
                self._send(404, "text/plain", b"not found")
                return
            size = os.path.getsize(fp)
            mime = mimetypes.guess_type(name)[0] or "application/octet-stream"
            self.send_response(200)
            self.send_header("Content-Type", mime)
            self.send_header("Content-Length", str(size))
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            sent = 0
            try:
                with open(fp, "rb") as f:
                    while sent < size:
                        chunk = f.read(CHUNK)
                        if not chunk:
                            break
                        self.wfile.write(chunk)
                        sent += len(chunk)
            except (BrokenPipeError, ConnectionResetError):
                pass
            self.log_message("%s /files/%s enviado=%d/%d COMPLETO|CORTADO", self.command,
                             name, sent, size)
            return
        self._send(404, "text/plain", b"not found")

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        if parsed.path not in ("/upload", "/"):
            self._send(404, "text/plain", b"not found")
            return
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > MAX_BODY:
            self._send(413 if length > MAX_BODY else 400, "text/plain", b"bad length")
            return
        body = self.rfile.read(length)
        ctype = self.headers.get("Content-Type", "")
        fname = None
        data = None
        if ctype.startswith("multipart/form-data"):
            fname, data = parse_multipart(body, ctype)
        elif parsed.query:
            qs = urllib.parse.parse_qs(parsed.query)
            fname = safe_name(qs.get("name", ["imagen.png"])[0])
            data = body
            if not ctype.startswith("image/") and not ctype:
                ctype = "image/jpeg"
        if not fname or data is None:
            self._send(400, "text/plain; charset=utf-8", b"no se pudo leer el archivo (usa -F file=@imagen.jpg)")
            return
        stem, ext = os.path.splitext(fname)
        ext = ext or (mimetypes.guess_extension(ctype)) or ".jpg"
        out = stem + "_" + shortid() + ext
        path = os.path.join(DIR_UPLOADS, out)
        with open(path, "wb") as f:
            f.write(data)
        link = "/files/" + out
        self.log_message("SAVED %s (%d bytes)", path, len(data))
        self._send(200, "text/html; charset=utf-8",
                     self._html("Subida OK",
                                "<p>Guardado: <a href=\"%s\">%s</a></p><p><code>%d bytes</code></p>"
                                % (link, out, len(data))))


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 12000
    DIR_UPLOADS = sys.argv[2] if len(sys.argv) > 2 else DIR_UPLOADS
    os.makedirs(DIR_UPLOADS, exist_ok=True)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()