// VCDN video deletion proxy.

// The VCDN_API_KEY NEVER enters the Android APK. The app calls this edge
// function with the user's JWT when deleting a post/reel/story/multimedia
// message; this function deletes the video from cdn.vcdn.me and cleans up the
// vcdn_upload_sessions row afterwards when reachable.
//
// Auth: user calls carry a Supabase JWT and are cryptographically verified
// through Supabase Auth. Server-side cleanup carries x-internal-secret.
// verify_jwt remains false because the cron path does not send a Supabase JWT.

const VCDN_BASE = "https://cdn.vcdn.me";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") || "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || Deno.env.get("SUPABASE_ANON_KEY") || "";

async function getVerifiedUserId(req: Request): Promise<string | null> {
  const auth = req.headers.get("Authorization") || "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
  if (!token || !SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) return null;

  try {
    const res = await fetch(
      `${SUPABASE_URL.replace(/\/$/, "")}/auth/v1/user`,
      {
        method: "GET",
        headers: {
          "apikey": SUPABASE_SERVICE_ROLE_KEY,
          "Authorization": `Bearer ${token}`,
        },
      },
    );
    if (!res.ok) return null;
    const user = await res.json();
    return typeof user?.id === "string" && user.id.length > 0 ? user.id : null;
  } catch {
    return null;
  }
}

export default {
  async fetch(req: Request): Promise<Response> {
    if (req.method !== "POST") {
      return Response.json({ error: "Method not allowed" }, { status: 405 });
    }

    const internalSecret = Deno.env.get("EDGE_INTERNAL_SECRET");
    const providedInternal = req.headers.get("x-internal-secret") || "";
    const isInternal = !!internalSecret && providedInternal === internalSecret;

    if (!isInternal && (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY)) {
      return Response.json({ error: "Server not configured" }, { status: 503 });
    }

    const userId = isInternal ? null : await getVerifiedUserId(req);
    if (!isInternal && !userId) {
      return Response.json({ error: "Unauthorized" }, { status: 401 });
    }

    let body: { videoId?: string };
    try {
      body = await req.json();
    } catch {
      return Response.json({ error: "Invalid JSON" }, { status: 400 });
    }

    const videoId = (body.videoId || "").trim();
    if (!videoId) {
      return Response.json({ error: "Missing videoId" }, { status: 400 });
    }

    const key = Deno.env.get("VCDN_API_KEY");
    if (!key) {
      return Response.json({ error: "VCDN storage is not configured" }, { status: 503 });
    }

    // User calls fail closed: deletion is allowed only when the local session
    // proves that the VCDN object belongs to the authenticated user.
    if (!isInternal) {
      try {
        const encodedVideoId = encodeURIComponent(videoId);
        const res = await fetch(
          `${SUPABASE_URL.replace(/\/$/, "")}/rest/v1/vcdn_upload_sessions?select=user_id&video_id=eq.${encodedVideoId}`,
          {
            method: "GET",
            headers: {
              "apikey": SUPABASE_SERVICE_ROLE_KEY,
              "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
            },
          },
        );
        if (!res.ok) {
          return Response.json({ error: "Ownership lookup failed" }, { status: 502 });
        }
        const rows = await res.json();
        if (!Array.isArray(rows) || rows.length === 0) {
          return Response.json({ error: "Forbidden: video ownership not found" }, { status: 403 });
        }
        if (rows.some((r) => r?.user_id !== userId)) {
          return Response.json({ error: "Forbidden: video does not belong to user" }, { status: 403 });
        }
      } catch (e) {
        console.error("vcdn-delete ownership check failed:", String(e));
        return Response.json({ error: "Ownership lookup failed" }, { status: 502 });
      }
    }

    let status: number;
    let detail: string;
    try {
      const headers = new Headers();
      headers.set("Authorization", `Bearer ${key}`);
      headers.set("Content-Type", "application/json");
      const del = await fetch(`${VCDN_BASE}/api/v1/videos/${encodeURIComponent(videoId)}`, {
        method: "DELETE",
        headers,
      });
      status = del.status;
      detail = (await del.text()).slice(0, 500);
      if (!del.ok && del.status !== 404) {
        return Response.json({ error: "VCDN delete failed", code: status, detail }, { status: 502 });
      }
    } catch (e) {
      return Response.json({ error: "VCDN delete error", detail: String(e) }, { status: 502 });
    }

    try {
      const encodedVideoId = encodeURIComponent(videoId);
      await fetch(
        `${SUPABASE_URL.replace(/\/$/, "")}/rest/v1/vcdn_upload_sessions?video_id=eq.${encodedVideoId}`,
        {
          method: "DELETE",
          headers: {
            "apikey": SUPABASE_SERVICE_ROLE_KEY,
            "Authorization": `Bearer ${SUPABASE_SERVICE_ROLE_KEY}`,
          },
        },
      );
    } catch (e) {
      console.error("vcdn-delete session cleanup failed:", String(e));
    }

    return Response.json({ ok: true, videoId, code: status });
  },
};
