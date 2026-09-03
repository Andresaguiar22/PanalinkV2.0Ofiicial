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

export default {
  async fetch(req: Request): Promise<Response> {
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
    // Identity must be stable per user; default to the caller's user id so the
    // app can always look up its own participant. Keep it LiveKit-safe (alnum + -).
    const rawIdentity = (body.identity || userId).trim();
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
      video: { room, roomJoin: true, canPublish: true, canSubscribe: true, canPublishData: true },
      sid: "", // server-assigned room id (left blank = create-or-join)
      name,
    };
    const encHeader = base64UrlEncode(strToBytes(JSON.stringify(header)));
    const encPayload = base64UrlEncode(strToBytes(JSON.stringify(payload)));
    const signingInput = `${encHeader}.${encPayload}`;
    const sig = base64UrlEncode(await hmacSha256(API_SECRET, signingInput));

    const token = `${signingInput}.${sig}`;
    return Response.json({ token, url: LIVEKIT_URL, identity, room });
  },
};
