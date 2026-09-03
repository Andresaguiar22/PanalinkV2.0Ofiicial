// Edge function invoked by the caller's app to notify the recipient of an
// incoming call via FCM. verify_jwt=true so the caller's JWT is validated at
// the gateway; the caller's id is passed in the body.
//
// The push is DATA-ONLY (no `notification` block) with high priority so that
// FCM spawns the app's FirebaseMessagingService even when the app is closed —
// onMessageReceived then starts the ringtone + incoming-call screen directly,
// rather than relying on the user tapping a tray notification.
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

async function sendFcmDataOnly(projectId: string, accessToken: string, deviceToken: string, data: Record<string, string>) {
  // No `notification` block: data-only so onMessageReceived is invoked even
  // when the app process is dead. High priority maximises immediate delivery.
  const message: any = { token: deviceToken, data, android: { priority: 'HIGH' } };
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` }, body: JSON.stringify({ message }) });
  if (res.ok) return { ok: true };
  const err = await res.text();
  return { ok: false, unregister: res.status === 404 || err.includes('UNREGISTERED') || err.includes('registration-token-not-registered'), error: `${res.status}: ${err}`.slice(0, 300) };
}

Deno.serve(async (req) => {
  try {
    if (req.method !== 'POST') return jsonResponse({ error: 'POST only' }, 405);
    // verify_jwt=true at the gateway validates the caller's JWT, so we do NOT
    // require the internal secret here (the app does not possess it). The
    // caller's id is taken from the JWT payload for traceability.
    if (!SERVICE_ACCOUNT_RAW) return jsonResponse({ error: 'FIREBASE_SERVICE_ACCOUNT secret not set' }, 500);

    let normalizedServiceAccount = SERVICE_ACCOUNT_RAW.trim().replace(/^```json\s*/i, '').replace(/```$/i, '').trim();
    if (!normalizedServiceAccount.startsWith('{')) normalizedServiceAccount = `{${normalizedServiceAccount}`;
    if (!normalizedServiceAccount.endsWith('}')) normalizedServiceAccount = `${normalizedServiceAccount}}`;
    const serviceAccount = JSON.parse(normalizedServiceAccount);
    const projectId = serviceAccount.project_id;

    const body = await req.json();
    const receiverId = String(body.receiver_id ?? '').trim();
    const callerId = String(body.caller_id ?? '').trim();
    const callerName = String(body.caller_name ?? 'Panalink').slice(0, 80);
    const callType = String(body.call_type ?? 'voice') === 'video' ? 'video' : 'voice';
    if (!receiverId) return jsonResponse({ error: 'receiver_id required' }, 400);

    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? `https://${projectId}.supabase.co`;
    const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '';
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (serviceRoleKey) { headers['apikey'] = serviceRoleKey; headers['Authorization'] = `Bearer ${serviceRoleKey}`; }

    let tokens: string[] = [];
    const tkRes = await fetch(`${supabaseUrl}/rest/v1/fcm_tokens?user_id=eq.${encodeURIComponent(receiverId)}&select=token`, { headers });
    if (tkRes.ok) {
      const rows: any[] = await tkRes.json();
      tokens = rows.map(r => r.token).filter((t: string) => t && !t.startsWith('device_fallback_'));
    }
    if (tokens.length === 0) {
      const udRes = await fetch(`${supabaseUrl}/rest/v1/user_devices?user_id=eq.${encodeURIComponent(receiverId)}&select=push_token,is_active&order=updated_at.desc`, { headers });
      if (udRes.ok) {
        const rows: any[] = await udRes.json();
        tokens = rows.filter(r => r.is_active !== false).map(r => r.push_token).filter((t: string) => t && !t.startsWith('device_fallback_'));
      }
    }
    if (tokens.length === 0) return jsonResponse({ ok: false, reason: 'NO_DEVICES' });

    const accessToken = await getAccessToken(serviceAccount);
    const data = {
      notification_type: 'llamada_entrante',
      callerId,
      callerName,
      callType,
      channel: 'panalink_calls_v3',
    };

    let sent = 0;
    const dead: string[] = [];
    for (const t of tokens) {
      const r = await sendFcmDataOnly(projectId, accessToken, t, data);
      if (r.ok) sent++;
      else if (r.unregister) dead.push(t);
    }
    if (dead.length > 0 && serviceRoleKey) {
      for (const t of dead) {
        await fetch(`${supabaseUrl}/rest/v1/fcm_tokens?token=eq.${encodeURIComponent(t)}`, { method: 'DELETE', headers });
      }
    }
    return jsonResponse({ ok: true, sent, total: tokens.length });
  } catch (e) {
    return jsonResponse({ error: String(e?.message ?? e) }, 500);
  }
});
