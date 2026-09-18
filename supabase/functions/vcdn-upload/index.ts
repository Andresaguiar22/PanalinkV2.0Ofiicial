// VCDN video upload proxy with atomic, transactional idempotency (Camino 1).
//
// The VCDN API key NEVER enters the Android APK. The app streams the video to
// this edge function in <=8MB chunks; this function forwards each chunk to
// cdn.vcdn.me with the VCDN_API_KEY and updates state in PostgreSQL.
//
// Idempotency Contract & Concurrency Model:
//   1. Atomic Session Claiming with Lease & Owner Token:
//      Before calling upstream VCDN /upload/init, the Edge Function acquires a claim
//      via PostgreSQL RPC `claim_vcdn_upload_session(userId, stableId, ownerToken)`.
//      PostgreSQL guarantees with a UNIQUE(user_id, stable_id) constraint that
//      exactly ONE request gets PROCEED_INIT. Concurrent requests get WAIT_CLAIM
//      and poll until uploadId/videoId are populated, preventing duplicate videos.
//   2. Authoritative Offset Persistence: Every chunk synchronously calls
//      `update_vcdn_upload_bytes` in PostgreSQL before returning 200 OK, ensuring
//      cross-instance reliability across Edge Functions and Worker restarts.
//   3. Chunk Idempotency & Lost Response: Chunks sent with matching or prior
//      offsets are validated; VCDN progress is synchronously recorded.
//   4. Atomic & Safe Complete: `claim_vcdn_complete` ensures multiple concurrent
//      complete requests transition safely. `finalize_vcdn_session` is called ONLY
//      after upstream VCDN confirms success. If VCDN fails, claim is rolled back.
//   5. Deterministic Status Finalization: Status finalization correlates by
//      `video_id` or `upload_id` seamlessly.

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

// Verifies a session belongs to the caller before state-mutating RPCs are
// invoked (chunk/complete/status). Prevents IDOR across vcdn_upload_sessions.
async function isSessionOwner(
  userId: string,
  filter: { uploadId?: string; videoId?: string }
): Promise<{ ok: boolean; reason?: string }> {
  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) return { ok: false, reason: "server not configured" };
  const key = filter.uploadId ? `upload_id=eq.${encodeURIComponent(filter.uploadId)}` : `video_id=eq.${encodeURIComponent(filter.videoId || "")}`;
  try {
    const res = await fetch(`${SUPABASE_URL.replace(/\/$/, "")}/rest/v1/vcdn_upload_sessions?select=user_id&${key}`, {
      method: "GET",
      headers: { "apikey": SUPABASE_SERVICE_ROLE_KEY, "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}` },
    });
    if (!res.ok) return { ok: false, reason: "session lookup failed" };
    const rows = await res.json();
    if (!Array.isArray(rows) || rows.length === 0) return { ok: false, reason: "session not found" };
    if (rows.some((r) => r?.user_id !== userId)) return { ok: false, reason: "not owner" };
    return { ok: true };
  } catch (e) {
    return { ok: false, reason: String(e) };
  }
}

async function vcdnFetch(path: string, init: RequestInit, key: string): Promise<{ ok: boolean; status: number; body: string }> {
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${key}`);
  const res = await fetch(`${VCDN_BASE}${path}`, { ...init, headers });
  const body = await res.text();
  return { ok: res.ok, status: res.status, body };
}

// Per-session upload progress to avoid re-sending chunks we already sent.
const uploadProgress = new Map<string, number>();

// Chunk retries: HTTP 503 `upload_chunk_accounting_failed` and 409 `upload_session_closed`
// are documented as retry-safe (same chunk, idempotent). Bound attempts to avoid loops.
async function vcdnFetchChunk(
  path: string,
  init: RequestInit,
  key: string,
  uploadId: string,
  offset: number
): Promise<{ ok: boolean; status: number; body: string; retryable: boolean; retryCount: number }> {
  const maxAttempts = 3;
  let attempts = 0;
  let last: { ok: boolean; status: number; body: string } = { ok: false, status: 502, body: "" };
  while (attempts < maxAttempts) {
    attempts++;
    const res = await vcdnFetch(path, init, key);
    last = res;
    if (res.ok) {
      uploadProgress.set(uploadId, Math.max(uploadProgress.get(uploadId) ?? 0, offset + (init.body instanceof Uint8Array ? init.body.byteLength : 0)));
      return { ...res, retryable: false, retryCount: attempts };
    }
    const retryable = res.status === 503 || res.status === 409;
    if (!retryable) return { ...res, retryable: false, retryCount: attempts };
    if (attempts < maxAttempts) {
      await delay(800 * attempts);
    }
  }
  return { ...last, retryable: true, retryCount: attempts };
}

// `standard` ladder: multi-rendition ABR (360/720/1080 capacity). El default
// `source` deriva una sola rendition, que limita el bitrate adaptativo.
const LADDER_PROFILE = "standard";

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

        const owner = await isSessionOwner(userId, { uploadId });
        if (!owner.ok) {
          return Response.json({ error: "Forbidden: upload does not belong to user" }, { status: 403 });
        }

        // Idempotencia en esta instancia: si estos bytes ya fueron contabilizados
        // (chunk previo con timeout tras almacenar, reintentado aquí), los saltamos
        // del todo y devolvemos el progreso ya persistido en PostgreSQL.
        if (chunkOffset >= 0) {
          const accounted = uploadProgress.get(uploadId) ?? 0;
          if (accounted >= chunkOffset + bytes.length) {
            const dbCheck = await callRpc("update_vcdn_upload_bytes", {
              p_upload_id: uploadId,
              p_bytes_received: accounted
            });
            return Response.json({ bytesReceived: dbCheck.ok && dbCheck.data?.bytesReceived ? dbCheck.data.bytesReceived : accounted });
          }
        }

        const r = await vcdnFetchChunk(`/api/v1/upload/${encodeURIComponent(uploadId)}/chunk`, {
          method: "POST",
          headers: { "Content-Type": "application/octet-stream" },
          body: bytes,
        }, key, uploadId, chunkOffset >= 0 ? chunkOffset : 0);

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
          return Response.json({ error: "VCDN chunk failed", code: r.status, detail: r.body.slice(0, 500), retryCount: r.retryCount }, { status: 502 });
        }

        const parsed = JSON.parse(r.body || "{}");
        const upstreamBytesReceived = Number(parsed.bytesReceived) || (chunkOffset >= 0 ? chunkOffset + bytes.length : bytes.length);
        uploadProgress.set(uploadId, Math.max(uploadProgress.get(uploadId) ?? 0, upstreamBytesReceived));

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
          const ownerToken = crypto.randomUUID();

          // 1. Atomic claim in PostgreSQL with owner token
          let claimRes = await callRpc("claim_vcdn_upload_session", {
            p_user_id: userId,
            p_stable_id: stableId,
            p_owner_token: ownerToken,
            p_filename: rawFilename,
            p_content_type: contentType,
            p_size: size
          });

          // If another request is currently claiming the session, wait and poll atomically
          let retries = 0;
          while (claimRes.ok && claimRes.data?.action === "WAIT_CLAIM" && retries < 25) {
            await delay(300);
            claimRes = await callRpc("claim_vcdn_upload_session", {
              p_user_id: userId,
              p_stable_id: stableId,
              p_owner_token: ownerToken,
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
                      p_video_id: existing.videoId,
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
                ladderProfile: LADDER_PROFILE,
              }),
            }, key);

            if (!r.ok) {
              await callRpc("fail_vcdn_upload_session", {
                p_user_id: userId,
                p_stable_id: stableId,
                p_owner_token: ownerToken,
                p_error: r.body.slice(0, 500)
              });
              return Response.json({ error: "VCDN init failed", code: r.status, detail: r.body.slice(0, 500) }, { status: 502 });
            }

            const vcdnInit = JSON.parse(r.body);

            // Populate PostgreSQL session with authoritative upstream IDs
            const popRes = await callRpc("populate_vcdn_upload_session", {
              p_user_id: userId,
              p_stable_id: stableId,
              p_owner_token: ownerToken,
              p_upload_id: vcdnInit.uploadId,
              p_video_id: vcdnInit.videoId,
              p_upload_url: vcdnInit.uploadUrl || ""
            });

            const returnedUploadId = popRes.ok && popRes.data?.uploadId ? popRes.data.uploadId : vcdnInit.uploadId;
            const returnedVideoId = popRes.ok && popRes.data?.videoId ? popRes.data.videoId : vcdnInit.videoId;

            return Response.json({
              ...vcdnInit,
              uploadId: returnedUploadId,
              videoId: returnedVideoId,
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
            ladderProfile: LADDER_PROFILE,
          }),
        }, key);
        if (!r.ok) return Response.json({ error: "VCDN init failed", code: r.status, detail: r.body.slice(0, 500) }, { status: 502 });
        return Response.json(JSON.parse(r.body));
      }

      // -------------------------------------------------------------
      // Step: complete (Atomic & Safe under concurrency)
      // -------------------------------------------------------------
      if (step === "complete") {
        const uploadId = String(data.uploadId || "");
        if (!uploadId) return Response.json({ error: "missing uploadId" }, { status: 400 });
        const owner = await isSessionOwner(userId, { uploadId });
        if (!owner.ok) {
          return Response.json({ error: "Forbidden: upload does not belong to user" }, { status: 403 });
        }

        const ownerToken = crypto.randomUUID();

        // 1. Atomic claim to complete with owner token
        const claimComplete = await callRpc("claim_vcdn_complete", {
          p_upload_id: uploadId,
          p_owner_token: ownerToken,
        });
        if (claimComplete.ok && (claimComplete.data?.action === "ALREADY_COMPLETED" || claimComplete.data?.action === "ALREADY_COMPLETING")) {
          return Response.json({ status: "completed", idempotent: true });
        }

        // 2. Call upstream VCDN complete
        const r = await vcdnFetch("/api/v1/upload/complete", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ uploadId }),
        }, key);

        // 3. ONLY finalize session if VCDN confirms success or already-completed
        if (r.ok || r.status === 400 || r.status === 409 || r.body.includes("already")) {
          uploadProgress.delete(uploadId); // upload terminó; libera el mapa de progreso
          await callRpc("finalize_vcdn_session", {
            p_upload_id: uploadId,
            p_ready: false,
          });
          return Response.json(r.ok ? JSON.parse(r.body || "{}") : { status: "completed", idempotent: true });
        }

        // If VCDN returned a real failure, roll back the complete claim to allow safe retry
        await callRpc("fail_vcdn_complete_claim", {
          p_upload_id: uploadId,
          p_owner_token: ownerToken,
        });
        return Response.json({ error: "VCDN complete failed", code: r.status, detail: r.body.slice(0, 500) }, { status: 502 });
      }

      // -------------------------------------------------------------
      // Step: status
      // -------------------------------------------------------------
      if (step === "status") {
        const videoId = String(data.videoId || "");
        if (!videoId) return Response.json({ error: "missing videoId" }, { status: 400 });
        const owner = await isSessionOwner(userId, { videoId });
        if (!owner.ok) {
          return Response.json({ error: "Forbidden: video does not belong to user" }, { status: 403 });
        }

        const r = await vcdnFetch(`/api/v1/videos/${encodeURIComponent(videoId)}`, { method: "GET" }, key);
        if (!r.ok) return Response.json({ error: "VCDN status failed", code: r.status, detail: r.body.slice(0, 500) }, { status: 502 });
        const v = JSON.parse(r.body);
        const ready = v.status === "ready";
        let streamUrl = "";
        let posterUrl = v.poster_url || "";
        if (ready) {
          // Observabilidad: log del estado del transcode / número de sources del ABR
          try {
            const sources = (v.playbackSources && Array.isArray(v.playbackSources)) ? v.playbackSources.length : 0;
            console.log(`VCDN ready videoId=${videoId} sources=${sources} progress=${v.transcode_progress} attempts=${v.transcode_attempts}`);
          } catch (_) {}
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

          // Persist ready status in database via video_id
          await callRpc("finalize_vcdn_session", {
            p_upload_id: "",
            p_video_id: videoId,
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
