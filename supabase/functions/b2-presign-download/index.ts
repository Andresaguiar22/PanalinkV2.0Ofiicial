import { S3Client, GetObjectCommand } from "npm:@aws-sdk/client-s3@3.1117.0";
import { getSignedUrl } from "npm:@aws-sdk/s3-request-presigner@3.1117.0";

// Re-signs an existing B2 object: returns a fresh presigned GET URL (7 days max).
// Called by the app when a stored media_url (B2 presigned GET) has expired or is
// about to expire. Auth: the caller's JWT (verify_jwt=true); the object key is
// resolved from the provided key (preferred) or from a legacy presigned URL.
const REGION = Deno.env.get("B2_REGION") || "us-east-005";
const ENDPOINT = `https://${Deno.env.get("B2_ENDPOINT") || "s3.us-east-005.backblazeb2.com"}`;
const BUCKET = Deno.env.get("B2_BUCKET") || "panalink-media-storage";
const GET_EXPIRES_IN = 604800; // 7 days (B2 max)

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

// Extract the object key from a legacy B2 presigned URL (path-style).
function urlToKey(url: string): string | null {
  try {
    const u = new URL(url);
    if (u.host !== (Deno.env.get("B2_ENDPOINT") || "s3.us-east-005.backblazeb2.com")) return null;
    const path = u.pathname.replace(/^\/+/, "");
    const prefix = BUCKET + "/";
    if (!path.startsWith(prefix)) return null;
    return decodeURIComponent(path.slice(prefix.length));
  } catch {
    return null;
  }
}

export default {
  async fetch(req: Request): Promise<Response> {
    if (req.method !== "POST") {
      return Response.json({ error: "Method not allowed" }, { status: 405 });
    }

    const userId = getUserId(req);
    if (!userId) {
      return Response.json({ error: "Unauthorized" }, { status: 401 });
    }

    const accessKey = Deno.env.get("B2_KEY_ID");
    const secretKey = Deno.env.get("B2_APP_KEY");
    if (!accessKey || !secretKey) {
      return Response.json({ error: "B2 storage is not configured" }, { status: 503 });
    }

    let body: { key?: string; url?: string };
    try {
      body = await req.json();
    } catch {
      return Response.json({ error: "Invalid JSON" }, { status: 400 });
    }

    // Prefer an explicit object key; fall back to extracting it from a legacy URL.
    let objectKey = (body.key && body.key.trim()) || null;
    if (!objectKey && body.url) objectKey = urlToKey(body.url);
    if (!objectKey) {
      return Response.json({ error: "Missing key or valid url" }, { status: 400 });
    }

    const s3 = new S3Client({
      region: REGION,
      endpoint: ENDPOINT,
      credentials: { accessKeyId: accessKey, secretAccessKey: secretKey },
      forcePathStyle: true,
      requestChecksumCalculation: "WHEN_REQUIRED",
      responseChecksumValidation: "WHEN_REQUIRED",
    });

    try {
      const getCommand = new GetObjectCommand({ Bucket: BUCKET, Key: objectKey });
      const publicUrl = await getSignedUrl(s3, getCommand, { expiresIn: GET_EXPIRES_IN });
      return Response.json({ publicUrl, key: objectKey, expiresIn: GET_EXPIRES_IN });
    } catch (e) {
      return Response.json({ error: "Failed to sign URL", detail: String(e) }, { status: 500 });
    }
  },
};
