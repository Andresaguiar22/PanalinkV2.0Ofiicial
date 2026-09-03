// VCDN video upload proxy with real idempotency (Camino 1).
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
// Idempotency Contract:
//   When a client provides a stable identity (`stableFileName`, `clientMessageUuid`,
//   or `customFileName`), the Edge Function checks for an existing upload session
//   bound to (userId, stableId). If found:
//     - If video is already ready in VCDN, it immediately returns { uploadId, videoId, ready: true, posterUrl }.
//     - If upload is in progress, it reuses the existing { uploadId, videoId, bytesReceived }.
//     - It never calls VCDN `/api/v1/upload/init` again, preventing duplicate video creation.
//
// Contract (POST):
//   step=chunk   -> raw body octet-stream, headers: x-vcdn-upload-id
//   JSON steps:  { "step": "init", "filename","size","contentType","title","stableFileName","clientMessageUuid" } -> { uploadId, videoId, uploadUrl, bytesReceived, ready, posterUrl }
//                 { "step": "complete", "uploadId" }                          -> { status }
//                 { "step": "status", "videoId" }                             -> { status, transcodeProgress, ready, streamUrl, posterUrl }
// Auth: caller's Supabase JWT (verify_jwt=true). User ID is extracted from sub.

const VCDN_BASE = "https://cdn.vcdn.me";
const BFF_BASE = "https://embed.vcdn.me";

interface SessionData {
  uploadId: string;
  videoId: string;
  uploadUrl?: string;
  bytesReceived: number;
  status: string;
  ready: boolean;
  posterUrl?: string;
  size: number;
  filename: string;
  contentType: string;
}

const sessionCache = new Map<string, SessionData>();

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

function sanitizeFileName(name: string): string {
  return name.replace(/[^a-zA-Z0-9._-]/g, "_").slice(0, 160);
}

async function vcdnFetch(path: string, init: RequestInit, key: string): Promise<{ ok: boolean; status: number; body: string }> {
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${key}`);
  const res = await fetch(`${VCDN_BASE}${path}`, { ...init, headers });
  const body = await res.text();
  return { ok: res.ok, status: res.status, body };
}

const SUPABASE_URL = Deno.env.get("SUPABASE_URL");
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || Deno.env.get("SUPABASE_ANON_KEY");

async function getDbSession(userId: string, stableId: string): Promise<SessionData | null> {
  const memKey = `${userId}:${stableId}`;
  const cached = sessionCache.get(memKey);
  if (cached) return cached;

  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) return null;
  try {
    const url = `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/vcdn_upload_sessions?user_id=eq.${encodeURIComponent(userId)}&stable_id=eq.${encodeURIComponent(stableId)}&select=*`;
    const res = await fetch(url, {
      headers: {
        "apikey": SUPABASE_SERVICE_ROLE_KEY,
        "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
        "Content-Type": "application/json"
      }
    });
    if (res.ok) {
      const rows = await res.json();
      if (rows && rows.length > 0) {
        const row = rows[0];
        const session: SessionData = {
          uploadId: row.upload_id,
          videoId: row.video_id,
          uploadUrl: row.upload_url || undefined,
          bytesReceived: Number(row.bytes_received) || 0,
          status: row.status || "initiated",
          ready: Boolean(row.ready),
          posterUrl: row.poster_url || "",
          size: Number(row.size) || 0,
          filename: row.filename,
          contentType: row.content_type
        };
        sessionCache.set(memKey, session);
        return session;
      }
    }
  } catch (e) {
    console.warn("getDbSession warning:", e);
  }
  return null;
}

async function saveDbSession(userId: string, stableId: string, session: SessionData): Promise<void> {
  const memKey = `${userId}:${stableId}`;
  sessionCache.set(memKey, session);

  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) return;
  try {
    const id = `${userId}:${stableId}`;
    const url = `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/vcdn_upload_sessions`;
    await fetch(url, {
      method: "POST",
      headers: {
        "apikey": SUPABASE_SERVICE_ROLE_KEY,
        "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
        "Content-Type": "application/json",
        "Prefer": "resolution=merge-duplicates"
      },
      body: JSON.stringify({
        id,
        user_id: userId,
        stable_id: stableId,
        upload_id: session.uploadId,
        video_id: session.videoId,
        upload_url: session.uploadUrl || null,
        filename: session.filename || null,
        content_type: session.contentType || null,
        size: session.size || null,
        bytes_received: session.bytesReceived || 0,
        status: session.status || "initiated",
        poster_url: session.posterUrl || null,
        ready: Boolean(session.ready),
        updated_at: new Date().toISOString()
      })
    });
  } catch (e) {
    console.warn("saveDbSession warning:", e);
  }
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
        const bytesReceived = Number(parsed.bytesReceived) || bytes.length;

        // Update session bytesReceived if found in cache
        for (const [k, sess] of sessionCache.entries()) {
          if (sess.uploadId === uploadId) {
            sess.bytesReceived = Math.max(sess.bytesReceived || 0, bytesReceived);
            const [uId, sId] = k.split(":");
            if (uId && sId) {
              saveDbSession(uId, sId, sess).catch(() => {});
            }
          }
        }

        return Response.json({ bytesReceived });
      }

      const data = await req.json();
      const step = data.step;

      if (step === "init") {
        const rawFilename = String(data.filename || "video.mp4");
        const size = Number(data.size) || 0;
        const contentType = String(data.contentType || "video/mp4");
        const title = String(data.title || "Panalink video");

        // Determine stable identity
        let stableId: string | null = null;
        if (data.stableFileName && String(data.stableFileName).trim()) {
          stableId = sanitizeFileName(String(data.stableFileName).trim());
        } else if (data.clientMessageUuid && String(data.clientMessageUuid).trim()) {
          stableId = sanitizeFileName(String(data.clientMessageUuid).trim());
        } else if (data.customFileName && String(data.customFileName).trim()) {
          stableId = sanitizeFileName(String(data.customFileName).trim());
        } else if (data.idempotencyKey && String(data.idempotencyKey).trim()) {
          stableId = sanitizeFileName(String(data.idempotencyKey).trim());
        }

        if (stableId) {
          const existing = await getDbSession(userId, stableId);
          if (existing) {
            // Re-use existing VCDN session; check if already ready
            let isReady = existing.ready;
            let posterUrl = existing.posterUrl;
            try {
              const r = await vcdnFetch(`/api/v1/videos/${encodeURIComponent(existing.videoId)}`, { method: "GET" }, key);
              if (r.ok) {
                const v = JSON.parse(r.body);
                if (v.status === "ready") {
                  isReady = true;
                  existing.ready = true;
                  existing.status = "ready";
                  existing.posterUrl = v.poster_url || existing.posterUrl;
                  posterUrl = existing.posterUrl;
                }
              }
            } catch (_) {}

            return Response.json({
              uploadId: existing.uploadId,
              videoId: existing.videoId,
              uploadUrl: existing.uploadUrl || "",
              bytesReceived: existing.bytesReceived,
              ready: isReady,
              status: existing.status,
              posterUrl: posterUrl || "",
              idempotentReused: true
            });
          }
        }

        // Fresh VCDN init
        const r = await vcdnFetch("/api/v1/upload/init", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            filename: rawFilename,
            size,
            contentType,
            title,
          }),
        }, key);
        if (!r.ok) return Response.json({ error: "VCDN init failed", code: r.status, detail: r.body.slice(0, 500) }, { status: 502 });
        const vcdnInit = JSON.parse(r.body);

        if (stableId && vcdnInit.uploadId && vcdnInit.videoId) {
          const session: SessionData = {
            uploadId: vcdnInit.uploadId,
            videoId: vcdnInit.videoId,
            uploadUrl: vcdnInit.uploadUrl,
            bytesReceived: 0,
            status: "initiated",
            ready: false,
            posterUrl: "",
            size,
            filename: rawFilename,
            contentType
          };
          await saveDbSession(userId, stableId, session);
        }

        return Response.json({
          ...vcdnInit,
          bytesReceived: 0,
          ready: false
        });
      }

      if (step === "complete") {
        const uploadId = String(data.uploadId || "");
        if (!uploadId) return Response.json({ error: "missing uploadId" }, { status: 400 });

        const r = await vcdnFetch("/api/v1/upload/complete", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ uploadId }),
        }, key);

        for (const [k, sess] of sessionCache.entries()) {
          if (sess.uploadId === uploadId) {
            sess.status = "completed";
            const [uId, sId] = k.split(":");
            if (uId && sId) {
              saveDbSession(uId, sId, sess).catch(() => {});
            }
          }
        }

        if (!r.ok) {
          if (r.status === 400 || r.status === 409 || r.body.includes("already")) {
            return Response.json({ status: "completed", idempotent: true });
          }
          return Response.json({ error: "VCDN complete failed", code: r.status, detail: r.body.slice(0, 500) }, { status: 502 });
        }
        return Response.json(JSON.parse(r.body || "{}"));
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

          for (const [k, sess] of sessionCache.entries()) {
            if (sess.videoId === videoId) {
              sess.ready = true;
              sess.status = "ready";
              sess.posterUrl = posterUrl;
              const [uId, sId] = k.split(":");
              if (uId && sId) {
                saveDbSession(uId, sId, sess).catch(() => {});
              }
            }
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
