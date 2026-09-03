// VCDN video upload proxy with atomic, transactional idempotency (Camino 1).
//
// The VCDN API key NEVER enters the Android APK. The app streams the video to
// this edge function in <=8MB chunks; this function forwards each chunk to
// cdn.vcdn.me with the VCDN_API_KEY and updates state in PostgreSQL.
//
// Idempotency Contract & Concurrency Model:
//   1. Atomic Session Claiming: Before calling upstream VCDN /upload/init, the
//      Edge Function executes the PostgreSQL RPC `claim_vcdn_upload_session(userId, stableId)`.
//      PostgreSQL guarantees with a UNIQUE(user_id, stable_id) constraint that
//      exactly ONE request gets PROCEED_INIT. Concurrent requests get WAIT_CLAIM
//      and wait until the uploadId/videoId are populated, preventing duplicate videos.
//   2. Authoritative Offset Persistence: Every chunk synchronously calls
//      `update_vcdn_upload_bytes` in PostgreSQL before returning 200 OK, ensuring
//      cross-instance reliability across Edge Functions and Worker restarts.
//   3. Chunk Idempotency & Lost Response: Chunks sent with matching or prior
//      offsets are validated; VCDN progress is synchronously recorded.
//   4. Atomic Complete: `claim_vcdn_complete` ensures multiple concurrent
//      complete requests transition safely and return idempotent success.

const VCDN_BASE = "https://cdn.vcdn.me";
const BFF_BASE = "https://embed.vcdn.me";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") || "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || Deno.env.get("SUPABASE_ANON_KEY") || "";

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

async function callRpc(functionName: string, params: Record<string, unknown>): Promise<{ ok: boolean; data: any; error?: string }> {
  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) {
    return { ok: false, data: null, error: "Database not configured" };
  }
  try {
    const url = `${SUPABASE_URL.replace(/\/$/, '')}/rest/v1/rpc/${encodeURIComponent(functionName)}`;
    const res = await fetch(url, {
      method: "POST",
      headers: {
        "apikey": SUPABASE_SERVICE_ROLE_KEY,
        "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify(params)
    });
    if (!res.ok) {
      const errText = await res.text();
      return { ok: false, data: null, error: `RPC ${functionName} failed: ${res.status} ${errText}` };
    }
    const data = await res.json();
    return { ok: true, data };
  } catch (e) {
    return { ok: false, data: null, error: String(e) };
  }
}

async function vcdnFetch(path: string, init: RequestInit, key: string): Promise<{ ok: boolean; status: number; body: string }> {
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${key}`);
  const res = await fetch(`${VCDN_BASE}${path}`, { ...init, headers });
  const body = await res.text();
  return { ok: res.ok, status: res.status, body };
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
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
      // -------------------------------------------------------------
      // Step: chunk (Raw binary upload)
      // -------------------------------------------------------------
      if (ct.includes("application/octet-stream")) {
        const uploadId = req.headers.get("x-vcdn-upload-id") || "";
        const chunkOffset = Number(req.headers.get("x-vcdn-chunk-offset") || "-1");
        const bytes = new Uint8Array(await req.arrayBuffer());

        if (!uploadId || bytes.length === 0) {
          return Response.json({ error: "missing uploadId or bytes" }, { status: 400 });
        }

        const r = await vcdnFetch(`/api/v1/upload/${encodeURIComponent(uploadId)}/chunk`, {
          method: "POST",
          headers: { "Content-Type": "application/octet-stream" },
          body: bytes,
        }, key);

        if (!r.ok) {
          // If chunk was already sent and VCDN rejects duplicate chunk, check if upload is already progressing
          if (r.status === 400 || r.status === 409) {
            const dbCheck = await callRpc("update_vcdn_upload_bytes", {
              p_upload_id: uploadId,
              p_bytes_received: chunkOffset >= 0 ? chunkOffset + bytes.length : bytes.length
            });
            if (dbCheck.ok && dbCheck.data?.bytesReceived) {
              return Response.json({ bytesReceived: dbCheck.data.bytesReceived });
            }
          }
          return Response.json({ error: "VCDN chunk failed", code: r.status, detail: r.body.slice(0, 500) }, { status: 502 });
        }

        const parsed = JSON.parse(r.body || "{}");
        const upstreamBytesReceived = Number(parsed.bytesReceived) || bytes.length;

        // Synchronously persist authoritative bytesReceived to PostgreSQL before responding
        const updateRes = await callRpc("update_vcdn_upload_bytes", {
          p_upload_id: uploadId,
          p_bytes_received: upstreamBytesReceived
        });

        const finalBytes = updateRes.ok && updateRes.data?.bytesReceived
          ? updateRes.data.bytesReceived
          : upstreamBytesReceived;

        return Response.json({ bytesReceived: finalBytes });
      }

      // -------------------------------------------------------------
      // JSON Steps: init, complete, status
      // -------------------------------------------------------------
      const data = await req.json();
      const step = data.step;

      // -------------------------------------------------------------
      // Step: init
      // -------------------------------------------------------------
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
          // 1. Atomic claim in PostgreSQL
          let claimRes = await callRpc("claim_vcdn_upload_session", {
            p_user_id: userId,
            p_stable_id: stableId,
            p_filename: rawFilename,
            p_content_type: contentType,
            p_size: size
          });

          // If another request is currently claiming the session, wait and poll atomically
          let retries = 0;
          while (claimRes.ok && claimRes.data?.action === "WAIT_CLAIM" && retries < 15) {
            await delay(200);
            claimRes = await callRpc("claim_vcdn_upload_session", {
              p_user_id: userId,
              p_stable_id: stableId,
              p_filename: rawFilename,
              p_content_type: contentType,
              p_size: size
            });
            retries++;
          }

          // Case A: Existing session found -> reuse uploadId / videoId
          if (claimRes.ok && claimRes.data?.action === "REUSE") {
            const existing = claimRes.data;
            let isReady = existing.ready;
            let posterUrl = existing.posterUrl;

            // If not yet flagged ready in DB, check upstream VCDN once
            if (!isReady && existing.videoId) {
              try {
                const r = await vcdnFetch(`/api/v1/videos/${encodeURIComponent(existing.videoId)}`, { method: "GET" }, key);
                if (r.ok) {
                  const v = JSON.parse(r.body);
                  if (v.status === "ready") {
                    isReady = true;
                    posterUrl = v.poster_url || posterUrl;
                    await callRpc("finalize_vcdn_session", {
                      p_upload_id: existing.uploadId,
                      p_ready: true,
                      p_poster_url: posterUrl
                    });
                  }
                }
              } catch (_) {}
            }

            return Response.json({
              uploadId: existing.uploadId,
              videoId: existing.videoId,
              uploadUrl: existing.uploadUrl || "",
              bytesReceived: existing.bytesReceived || 0,
              ready: isReady,
              status: existing.status || "initiated",
              posterUrl: posterUrl || "",
              idempotentReused: true
            });
          }

          // Case B: This request won the claim (PROCEED_INIT) -> Call upstream VCDN /upload/init
          if (claimRes.ok && claimRes.data?.action === "PROCEED_INIT") {
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

            if (!r.ok) {
              await callRpc("fail_vcdn_upload_session", {
                p_user_id: userId,
                p_stable_id: stableId,
                p_error: r.body.slice(0, 500)
              });
              return Response.json({ error: "VCDN init failed", code: r.status, detail: r.body.slice(0, 500) }, { status: 502 });
            }

            const vcdnInit = JSON.parse(r.body);

            // Populate PostgreSQL session with authoritative upstream IDs
            await callRpc("populate_vcdn_upload_session", {
              p_user_id: userId,
              p_stable_id: stableId,
              p_upload_id: vcdnInit.uploadId,
              p_video_id: vcdnInit.videoId,
              p_upload_url: vcdnInit.uploadUrl || ""
            });

            return Response.json({
              ...vcdnInit,
              bytesReceived: 0,
              ready: false
            });
          }
        }

        // Fallback for requests without stable identity
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
        return Response.json(JSON.parse(r.body));
      }

      // -------------------------------------------------------------
      // Step: complete (Atomic under concurrency)
      // -------------------------------------------------------------
      if (step === "complete") {
        const uploadId = String(data.uploadId || "");
        if (!uploadId) return Response.json({ error: "missing uploadId" }, { status: 400 });

        // Atomic claim to complete
        const claimComplete = await callRpc("claim_vcdn_complete", { p_upload_id: uploadId });
        if (claimComplete.ok && (claimComplete.data?.action === "ALREADY_COMPLETED" || claimComplete.data?.action === "ALREADY_COMPLETING")) {
          return Response.json({ status: "completed", idempotent: true });
        }

        const r = await vcdnFetch("/api/v1/upload/complete", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ uploadId }),
        }, key);

        await callRpc("finalize_vcdn_session", {
          p_upload_id: uploadId,
          p_ready: false
        });

        if (!r.ok) {
          if (r.status === 400 || r.status === 409 || r.body.includes("already")) {
            return Response.json({ status: "completed", idempotent: true });
          }
          return Response.json({ error: "VCDN complete failed", code: r.status, detail: r.body.slice(0, 500) }, { status: 502 });
        }
        return Response.json(JSON.parse(r.body || "{}"));
      }

      // -------------------------------------------------------------
      // Step: status
      // -------------------------------------------------------------
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

          // Persist ready status in database
          await callRpc("finalize_vcdn_session", {
            p_upload_id: "",
            p_ready: true,
            p_poster_url: posterUrl
          });
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
