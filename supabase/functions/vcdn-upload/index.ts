// VCDN video upload proxy (Camino 1).
//
// The VCDN API key NEVER enters the Android APK. The app streams the video to
// this edge function in <=8MB chunks (Supabase edge limit = 10MB body); this
// function forwards each chunk to cdn.vcdn.me with the VCDN_API_KEY and returns
// the progress. After all chunks are uploaded, it finalizes the upload and polls
// the transcode status; once "ready", it resolves a fresh HLS streamUrl via the
// public BFF (embed.vcdn.me/api/bff/player-config/{videoId}) so the app only ever
// stores the stable vcdn_video_id (the signed streamUrl expires and is renewed
// at playback time by VcdnUrlResolver).
//
// Contract (POST):
//   step=chunk   -> raw body octet-stream, headers: x-vcdn-upload-id
//   JSON steps:  { "step": "init", "filename","size","contentType","title" } -> { uploadId, videoId, uploadUrl }
//                 { "step": "complete", "uploadId" }                          -> { status }
//                 { "step": "status", "videoId" }                             -> { status, transcodeProgress, ready, streamUrl, posterUrl }
// Auth: caller's Supabase JWT (verify_jwt=true). We extract sub for logging only.

const VCDN_BASE = "https://cdn.vcdn.me";
const BFF_BASE = "https://embed.vcdn.me";

function getUserId(req: Request): string | null {
  const auth = req.headers.get("Authorization") || "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
  if (!token) return null;
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const payload = JSON.parse(atob(parts[1].replace(/-/g, "+").replace(/_/g, "/")));
    return typeof payload.sub === "string" ? payload.sub : null;
  } catch {
    return null;
  }
}

async function vcdnFetch(path: string, init: RequestInit, key: string): Promise<{ ok: boolean; status: number; body: string }> {
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${key}`);
  const res = await fetch(`${VCDN_BASE}${path}`, { ...init, headers });
  const body = await res.text();
  return { ok: res.ok, status: res.status, body };
}

export default {
  async fetch(req: Request): Promise<Response> {
    if (req.method !== "POST") {
      return Response.json({ error: "Method not allowed" }, { status: 405 });
    }
    const userId = getUserId(req);
    if (!userId) return Response.json({ error: "Unauthorized" }, { status: 401 });

    const key = Deno.env.get("VCDN_API_KEY");
    if (!key) {
      console.error("VCDN_API_KEY secret not set");
      return Response.json({ error: "VCDN storage is not configured" }, { status: 503 });
    }

    const ct = req.headers.get("Content-Type") || "";
    try {
      // Chunk step carries raw bytes in the body (Content-Type: application/octet-stream).
      if (ct.includes("application/octet-stream")) {
        const uploadId = req.headers.get("x-vcdn-upload-id") || "";
        const bytes = new Uint8Array(await req.arrayBuffer());
        if (!uploadId || bytes.length === 0) {
          return Response.json({ error: "missing uploadId or bytes" }, { status: 400 });
        }
        const r = await vcdnFetch(`/api/v1/upload/${encodeURIComponent(uploadId)}/chunk`, {
          method: "POST",
          headers: { "Content-Type": "application/octet-stream" },
          body: bytes,
        }, key);
        if (!r.ok) return Response.json({ error: "VCDN chunk failed", code: r.status, detail: r.body.slice(0, 500) }, { status: 502 });
        const parsed = JSON.parse(r.body || "{}");
        return Response.json({ bytesReceived: Number(parsed.bytesReceived) || bytes.length });
      }

      const data = await req.json();
      const step = data.step;

      if (step === "init") {
        const r = await vcdnFetch("/api/v1/upload/init", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            filename: String(data.filename || "video.mp4"),
            size: Number(data.size),
            contentType: String(data.contentType || "video/mp4"),
            title: String(data.title || "Panalink video"),
          }),
        }, key);
        if (!r.ok) return Response.json({ error: "VCDN init failed", code: r.status, detail: r.body.slice(0, 500) }, { status: 502 });
        return Response.json(JSON.parse(r.body));
      }

      if (step === "complete") {
        const r = await vcdnFetch("/api/v1/upload/complete", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ uploadId: String(data.uploadId) }),
        }, key);
        if (!r.ok) return Response.json({ error: "VCDN complete failed", code: r.status, detail: r.body.slice(0, 500) }, { status: 502 });
        return Response.json(JSON.parse(r.body));
      }

      if (step === "status") {
        const videoId = String(data.videoId || "");
        if (!videoId) return Response.json({ error: "missing videoId" }, { status: 400 });
        const r = await vcdnFetch(`/api/v1/videos/${encodeURIComponent(videoId)}`, { method: "GET" }, key);
        if (!r.ok) return Response.json({ error: "VCDN status failed", code: r.status, detail: r.body.slice(0, 500) }, { status: 502 });
        const v = JSON.parse(r.body);
        const ready = v.status === "ready";
        let streamUrl = "";
        let posterUrl = v.poster_url || "";
        if (ready) {
          // Resolve a fresh signed HLS streamUrl from the public BFF (no auth needed).
          try {
            const cfg = await fetch(`${BFF_BASE}/api/bff/player-config/${encodeURIComponent(videoId)}`, { method: "GET" });
            if (cfg.ok) {
              const cj = await cfg.json();
              streamUrl = cj.streamUrl || (cj.playbackSources && cj.playbackSources[0] && cj.playbackSources[0].streamUrl) || "";
              posterUrl = cj.posterUrl || posterUrl;
            }
          } catch (e) {
            console.error("BFF player-config failed:", e);
          }
        }
        return Response.json({
          status: v.status,
          transcodeProgress: v.transcode_progress ?? v.progress ?? 0,
          ready,
          streamUrl,
          posterUrl,
        });
      }

      return Response.json({ error: "unknown step" }, { status: 400 });
    } catch (e) {
      console.error("vcdn-upload error:", e);
      return Response.json({ error: String(e) }, { status: 500 });
    }
  },
};
