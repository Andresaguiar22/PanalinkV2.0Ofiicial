// Mints a short-lived LiveKit participant token (JWT HS256) so the Android app
// can connect to a LiveKit room. The API secret never leaves the server — the
// device only receives a signed JWT. Auth: the caller's Supabase JWT
// (verify_jwt=true); the participant identity is derived from the JWT subject.
//
// Contract: POST { room, identity?, name?, ttl? } -> { token, url }
//   - room: the LiveKit room name (e.g. "call_<chatId>" or "voice_<roomId>")
//   - identity: stable participant id (defaults to the caller's user id)
//   - name: display name (optional)
//   - ttl: token validity seconds (default 3600 = 1h)
const LIVEKIT_URL = Deno.env.get("LIVEKIT_URL");
const API_KEY = Deno.env.get("LIVEKIT_API_KEY");
const API_SECRET = Deno.env.get("LIVEKIT_API_SECRET");

const SUPABASE_URL = (Deno.env.get("SUPABASE_URL") || "").replace(/\/$/, "");
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || Deno.env.get("SUPABASE_ANON_KEY") || "";

// Verifies access to Live rooms from the database. The requested role is never trusted:
// publish permission is derived server-side so a normal viewer cannot publish.
//
// CRITICAL: every Supabase query here uses the SERVICE ROLE key so the caller's
// RLS (e.g. `status='LIVE' OR host_id=auth.uid()`) is NOT applied. Authorization
// is decided exclusively by this function. The host of a live stream may mint a
// token even while the stream is still CREATED (the client PATCHes it to LIVE
// right after), which fixes the "No se pudo conectar con el servidor de video"
// 403 caused by a race between the token request and the status update.
async function canJoinRoom(userId: string, room: string): Promise<{ ok: boolean; publish: boolean; reason?: string }> {
  if (room.startsWith("live_")) {
    // The Android client generates a random UUID for the room name
    // (live_<random uuid>) that is stored in live_streams.room_name — it is NOT
    // the stream's PK (id). Lookups must filter by room_name, never by id.
    const roomName = room;
    const roomUuid = room.slice("live_".length);
    if (!/^[0-9a-fA-F-]{36}$/.test(roomUuid)) return { ok: false, publish: false, reason: "invalid live room" };
    if (!SUPABASE_URL || !SERVICE_KEY) return { ok: false, publish: false, reason: "server not configured" };
    try {
      const headers = { "apikey": SERVICE_KEY, "Authorization": `Bearer ${SERVICE_KEY}`, "Content-Type": "application/json" };
      const streamRes = await fetch(
        `${SUPABASE_URL}/rest/v1/live_streams?room_name=eq.${roomName}&select=id,host_id,status&limit=1`,
        { headers },
      );
      if (!streamRes.ok) return { ok: false, publish: false, reason: "stream lookup failed" };
      const streams = await streamRes.json();
      const stream = Array.isArray(streams) ? streams[0] : null;
      if (!stream) return { ok: false, publish: false, reason: "live stream not found" };
      const streamId = stream.id;

      // The host can always join (and publish) — even before the client flips the
      // stream to LIVE. Any other participant requires the stream to be LIVE.
      if (stream.host_id === userId) return { ok: true, publish: true };

      if (stream.status !== "LIVE") return { ok: false, publish: false, reason: "live stream is not active" };

      const guestRes = await fetch(
        `${SUPABASE_URL}/rest/v1/live_guests?stream_id=eq.${streamId}&guest_user_id=eq.${userId}&status=in.(ACCEPTED,ACTIVE,CONNECTED)&select=id,status&limit=1`,
        { headers },
      );
      if (!guestRes.ok) return { ok: false, publish: false, reason: "guest lookup failed" };
      const guests = await guestRes.json();
      if (Array.isArray(guests) && guests.length > 0) return { ok: true, publish: true };
      return { ok: true, publish: false };
    } catch {
      return { ok: false, publish: false, reason: "live access check failed" };
    }
  }
  if (room.startsWith("voice_")) {
    const roomId = room.slice("voice_".length);
    if (!/^[0-9a-fA-F-]{36}$/.test(roomId)) return { ok: false, publish: false, reason: "invalid voice room" };
    if (!SUPABASE_URL || !SERVICE_KEY) return { ok: false, publish: false, reason: "server not configured" };
    try {
      const res = await fetch(`${SUPABASE_URL}/rest/v1/rpc/voice_room_can_access`, {
        method: "POST",
        headers: { "apikey": SERVICE_KEY, "Authorization": `Bearer ${SERVICE_KEY}`, "Content-Type": "application/json" },
        body: JSON.stringify({ p_room_id: roomId }),
      });
      return { ok: res.ok, publish: res.ok, reason: res.ok ? undefined : "access denied" };
    } catch {
      return { ok: false, publish: false, reason: "access check failed" };
    }
  }
  if (room.startsWith("call_")) {
    const pair = room.slice("call_".length);
    // Room names encode two participant UUIDs as call_<uuidA>-<uuidB>. A UUID
    // itself contains hyphens, so split("-") would explode a single UUID. Match
    // exactly two well-formed UUIDs separated by the single inter-UUID hyphen.
    const match = pair.match(
      /^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})-([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$/,
    );
    if (!match) return { ok: false, publish: false, reason: "invalid call room" };
    const [, userA, userB] = match;
    return { ok: userA === userId || userB === userId, publish: userA === userId || userB === userId };
  }
  return { ok: false, publish: false, reason: "unsupported room" };
}

function base64UrlEncode(input: Uint8Array | ArrayBuffer): string {
  const bytes = input instanceof Uint8Array ? input : new Uint8Array(input);
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function strToBytes(s: string): Uint8Array {
  return Uint8Array.from(Array.from(s).map((c) => c.charCodeAt(0)));
}

async function hmacSha256(key: string, message: string): Promise<ArrayBuffer> {
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    strToBytes(key),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  return crypto.subtle.sign("HMAC", cryptoKey, strToBytes(message));
}

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

async function handler(req) {
    if (req.method !== "POST") {
      return Response.json({ error: "Method not allowed" }, { status: 405 });
    }

    if (!LIVEKIT_URL || !API_KEY || !API_SECRET) {
      return Response.json({ error: "LiveKit is not configured" }, { status: 503 });
    }

    const userId = getUserId(req);
    if (!userId) {
      return Response.json({ error: "Unauthorized" }, { status: 401 });
    }

    let body: { room?: string; identity?: string; name?: string; ttl?: number };
    try {
      body = await req.json();
    } catch {
      return Response.json({ error: "Invalid JSON" }, { status: 400 });
    }

    const room = (body.room || "").trim();
    if (!room) {
      return Response.json({ error: "Missing room" }, { status: 400 });
    }

    const access = await canJoinRoom(userId, room);
    if (!access.ok) {
      return Response.json({ error: "Forbidden: no access to room" }, { status: 403 });
    }

    // Identity must be stable per user: always pinned to the caller's JWT subject
    // (auth.uid()). The body.identity is NEVER trusted for authorization guarantees
    // (the Android client used to send a fake ``host_<timestamp>`` identity when
    // the current session was null — that produced unstable LiveKit participant ids).
    // The name for display is still taken from the body (harmless).

    const rawIdentity = userId;
    const identity = rawIdentity.replace(/[^a-zA-Z0-9_\-]/g, "_").slice(0, 120);
    const name = (body.name || "").trim().slice(0, 120) || undefined;
    const ttl = Math.min(Math.max(Number(body.ttl) || 3600, 60), 86400);

    // LiveKit Access Token (JWT) — see https://docs.livekit.io/home/get-started/identity-and-tokens
    const now = Math.floor(Date.now() / 1000);
    const header = { typ: "JWT", alg: "HS256", kid: API_KEY };
    const payload = {
      iss: API_KEY,
      sub: identity,
      iat: now,
      exp: now + ttl,
      nbf: now,
      // Grant: can publish + subscribe to audio/video (camera/mic/screen) and data.
      video: { room, roomJoin: true, canPublish: access.publish, canSubscribe: true, canPublishData: true },
      sid: "", // server-assigned room id (left blank = create-or-join)
      name,
    };
    const encHeader = base64UrlEncode(strToBytes(JSON.stringify(header)));
    const encPayload = base64UrlEncode(strToBytes(JSON.stringify(payload)));
    const signingInput = `${encHeader}.${encPayload}`;
    const sig = base64UrlEncode(await hmacSha256(API_SECRET, signingInput));

    const token = `${signingInput}.${sig}`;
    return Response.json({ token, url: LIVEKIT_URL, identity, room });
}

Deno.serve(handler);
