const INTERNAL_SECRET = Deno.env.get('EDGE_INTERNAL_SECRET') ?? '';
const SERVICE_ACCOUNT_RAW = Deno.env.get('FIREBASE_SERVICE_ACCOUNT') ?? '';

function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), { status, headers: { 'Content-Type': 'application/json' } });
}

function b64url(input: string | Uint8Array): string {
  const bytes = typeof input === 'string' ? new TextEncoder().encode(input) : input;
  let bin = '';
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function pemToDer(pem: string): Uint8Array {
  const b64 = pem.replace(/-----BEGIN PRIVATE KEY-----/g, '').replace(/-----END PRIVATE KEY-----/g, '').replace(/\s+/g, '');
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

let cachedToken: { token: string; expiresAt: number } | null = null;

async function getAccessToken(sa: any): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.expiresAt > now + 60) return cachedToken.token;
  const header = { alg: 'RS256', typ: 'JWT' };
  const claims = { iss: sa.client_email, scope: 'https://www.googleapis.com/auth/firebase.messaging', aud: 'https://oauth2.googleapis.com/token', iat: now, exp: now + 3600 };
  const unsigned = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(claims))}`;
  const key = await crypto.subtle.importKey('pkcs8', pemToDer(sa.private_key), { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned));
  const jwt = `${unsigned}.${b64url(new Uint8Array(signature))}`;
  const res = await fetch('https://oauth2.googleapis.com/token', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}` });
  const json = await res.json();
  if (!json.access_token) throw new Error(`OAuth token exchange failed: ${JSON.stringify(json)}`);
  cachedToken = { token: json.access_token, expiresAt: now + (json.expires_in ?? 3600) };
  return json.access_token;
}

async function sendFcm(projectId: string, accessToken: string, deviceToken: string, title: string, body: string, channel: string, data: Record<string, string>) {
  // Data-only message: NO notification block. With a notification block, FCM
  // tries to display the tray notification directly when the app is killed —
  // but the process is dead so the notification channel doesn't exist and
  // Android 8+ silently drops it. Data-only messages ALWAYS invoke
  // onMessageReceived (even with app killed), where the service creates
  // the channel and builds the notification itself.
  const message: any = {
    token: deviceToken,
    data: { ...data, title: String(title), body: String(body), channel_id: String(channel) },
    android: { priority: 'HIGH' }
  };
  if (data.chat_id) message.android.collapse_key = `chat_${data.chat_id}`;
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` }, body: JSON.stringify({ message }) });
  if (res.ok) return { ok: true };
  const err = await res.text();
  return { ok: false, unregister: res.status === 404 || err.includes('UNREGISTERED') || err.includes('registration-token-not-registered'), error: `${res.status}: ${err}`.slice(0, 300) };
}

Deno.serve(async (req) => {
  try {
    if (req.method !== 'POST') return jsonResponse({ error: 'POST only' }, 405);
    if (INTERNAL_SECRET && (req.headers.get('x-internal-secret') ?? '') !== INTERNAL_SECRET) return jsonResponse({ error: 'unauthorized' }, 401);
    if (!SERVICE_ACCOUNT_RAW) return jsonResponse({ error: 'FIREBASE_SERVICE_ACCOUNT secret not set' }, 500);

    // Tolerate the current Vault value if it was pasted without the opening '{'.
    let normalizedServiceAccount = SERVICE_ACCOUNT_RAW.trim().replace(/^```json\s*/i, '').replace(/```$/i, '').trim();
    if (!normalizedServiceAccount.startsWith('{')) normalizedServiceAccount = `{${normalizedServiceAccount}`;
    if (!normalizedServiceAccount.endsWith('}')) normalizedServiceAccount = `${normalizedServiceAccount}}`;
    const serviceAccount = JSON.parse(normalizedServiceAccount);

    const projectId: string = serviceAccount.project_id;
    if (!projectId || !serviceAccount.client_email || !serviceAccount.private_key) return jsonResponse({ error: 'invalid Firebase service account' }, 500);
    const payload = await req.json();
    const userId: string = payload.user_id ?? '';
    if (!userId) return jsonResponse({ error: 'user_id required' }, 400);
    const title = payload.title ?? 'PanaLink';
    const body = payload.body ?? '';
    const channel = payload.channel ?? 'panalink_messages_v3';
    const extraData: Record<string, string> = payload.data ?? {};
    const sbUrl = Deno.env.get('SUPABASE_URL') ?? '';
    const sbKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '';
    const rest = async (path: string, init?: RequestInit): Promise<any> => {
      const res = await fetch(`${sbUrl}/rest/v1/${path}`, { ...init, headers: { apikey: sbKey, Authorization: `Bearer ${sbKey}`, 'Content-Type': 'application/json', ...(init?.headers ?? {}) } });
      if (res.status === 204) return null;
      return res.json().catch(() => null);
    };
    const fcmRows: any[] = await rest(`fcm_tokens?select=user_id,token,platform&user_id=eq.${userId}`) ?? [];
    const seen = new Set<string>();
    const devices: { id: string | null; token: string }[] = [];
    for (const row of fcmRows) {
      const token = String(row.token ?? '');
      if (token && !token.startsWith('device_fallback_') && !seen.has(token)) { seen.add(token); devices.push({ id: null, token }); }
    }
    if (devices.length === 0) {
      const rows: any[] = await rest(`user_devices?select=id,fcm_token&user_id=eq.${userId}&is_active=eq.true`) ?? [];
      for (const row of rows) {
        const token = String(row.fcm_token ?? '');
        if (token && !token.startsWith('device_fallback_') && !seen.has(token)) { seen.add(token); devices.push({ id: row.id ?? null, token }); }
      }
    }
    if (devices.length === 0) return jsonResponse({ status: 'NO_DEVICES', sent: 0, failed: 0 });
    const data: Record<string, string> = { title: String(title), body: String(body), ...Object.fromEntries(Object.entries(extraData).map(([k, v]) => [k, String(v)])) };
    // FCM v1 rejects certain data keys that collide with its internal message
    // fields (e.g. "message_type" → "message.type"). Sanitize reserved keys
    // by prefixing with "p_" so the payload always sends.
    const fcmReservedKeys = new Set(['message_type', 'type', 'message', 'notification', 'android', 'apns', 'fcm_options', 'webpush', 'token']);
    const sanitizedData: Record<string, string> = {};
    for (const [k, v] of Object.entries(data)) {
      if (fcmReservedKeys.has(k)) sanitizedData[`p_${k}`] = v;
      else sanitizedData[k] = v;
    }
    const accessToken = await getAccessToken(serviceAccount);
    let sent = 0, failed = 0;
    const errors: string[] = [];
    for (const device of devices) {
      const result = await sendFcm(projectId, accessToken, device.token, String(title), String(body), String(channel), sanitizedData);
      if (result.ok) sent++; else { failed++; if (result.error) errors.push(result.error); if (result.unregister && device.id) await rest(`user_devices?id=eq.${device.id}`, { method: 'PATCH', body: JSON.stringify({ is_active: false }) }); }
    }
    return jsonResponse({ status: 'OK', sent, failed, errors: errors.slice(0, 3) });
  } catch (err: any) {
    console.error('send-push error:', err);
    return jsonResponse({ error: err?.message ?? 'unknown' }, 500);
  }
});
