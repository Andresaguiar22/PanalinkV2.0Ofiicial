#!/usr/bin/env python3
"""Servidor de APKs con soporte de Range (reanudable).

Uso: python3 serve_apk.py <puerto> <directorio>

Por que NO usar `python -m http.server`: esa version NO manda
`Accept-Ranges`/`206`, y con un APK de decenas de MB la descarga del movil se
corta y Android lo reporta como "paquete no valido". Aqui cada respuesta lleva
`Accept-Ranges: bytes`, `Content-Length` exacto y `Cache-Control: no-store`, y
se loguea `enviado=N/total COMPLETO|CORTADO` para distinguir una descarga
truncada de una completa.

No hereda de SimpleHTTPRequestHandler a proposito: en Python 3.13 el truco de
sobrescribir send_head() devolviendo una tupla rompe el do_HEAD heredado
('tuple' object has no attribute 'close').
"""
import mimetypes
import os
import re
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = "/tmp"
CHUNK = 64 * 1024


def safe_name(path: str) -> str:
    name = os.path.basename(path)
    if not name or name in (".", ".."):
        return ""
    return name


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "PanalinkServeApk/1.0"

    def log_message(self, fmt, *args):
        sys.stderr.write("[%s] %s\n" % (self.log_date_time_string(), fmt % args))

    def _headers(self, code, ctype, length, extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(length))
        self.send_header("Cache-Control", "no-store")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()

    def _resolve(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        if path.startswith("/apk/"):
            path = path[len("/apk/"):]
        return safe_name(urllib.parse.unquote(path))

    def _file_info(self):
        name = self._resolve()
        if not name:
            return None, None
        fp = os.path.join(ROOT, name)
        if not os.path.isfile(fp):
            return name, None
        return name, fp

    def do_HEAD(self):
        name, fp = self._file_info()
        if fp is None:
            self._headers(404, "text/plain", 9)
            return
        size = os.path.getsize(fp)
        mime = mimetypes.guess_type(name)[0] or "application/octet-stream"
        self._headers(200, mime, size, {"Accept-Ranges": "bytes"})

    def do_GET(self):
        if self.path in ("/", "/index.html"):
            self._index()
            return
        name, fp = self._file_info()
        if fp is None:
            self._headers(404, "text/plain", 9)
            self.wfile.write(b"not found")
            return

        size = os.path.getsize(fp)
        mime = mimetypes.guess_type(name)[0] or "application/octet-stream"
        start, end = 0, size - 1
        rng = self.headers.get("Range")
        partial = False
        if rng:
            m = re.match(r"bytes=(\d*)-(\d*)", rng.strip())
            if m and (m.group(1) or m.group(2)):
                start = int(m.group(1)) if m.group(1) else max(0, size - int(m.group(2)))
                if m.group(2):
                    end = min(int(m.group(2)), size - 1)
                if start > end or start >= size:
                    self._headers(416, "text/plain", 0,
                                  {"Content-Range": "bytes */%d" % size})
                    return
                partial = True

        length = end - start + 1
        extra = {"Accept-Ranges": "bytes"}
        if partial:
            extra["Content-Range"] = "bytes %d-%d/%d" % (start, end, size)
        self._headers(206 if partial else 200, mime, length, extra)

        sent = 0
        try:
            with open(fp, "rb") as f:
                f.seek(start)
                remaining = length
                while remaining > 0:
                    chunk = f.read(min(CHUNK, remaining))
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    sent += len(chunk)
                    remaining -= len(chunk)
        except (BrokenPipeError, ConnectionResetError):
            pass
        state = "COMPLETO" if sent == length else "CORTADO"
        self.log_message("%s %s enviado=%d/%d %s", self.command, name,
                         sent, length, state)

    def _index(self):
        try:
            names = sorted(os.listdir(ROOT))
        except OSError:
            names = []
        apks = [n for n in names if n.lower().endswith(".apk")]
        rows = "".join('<li><a href="/apk/%s">%s</a></li>' % (n, n) for n in apks)
        body = ("<!DOCTYPE html><html><body><h3>APKs disponibles</h3><ul>"
                + (rows or "<li>(ninguno)</li>")
                + "</ul></body></html>").encode("utf-8")
        self._headers(200, "text/html; charset=utf-8", len(body))
        self.wfile.write(body)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 12001
    ROOT = sys.argv[2] if len(sys.argv) > 2 else "/tmp"
    os.makedirs(ROOT, exist_ok=True)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
