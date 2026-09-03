import { S3Client, PutObjectCommand, GetObjectCommand } from "npm:@aws-sdk/client-s3@3.1117.0";
import { getSignedUrl } from "npm:@aws-sdk/s3-request-presigner@3.1117.0";

// Backblaze B2 credentials live only in Edge Function Secrets (never in the APK).
// The device uploads directly to B2 via a short-lived presigned PUT URL, and
// later serves the object through a long-lived presigned GET URL (B2 private
// bucket: no permanent public URLs without a custom domain, so we sign GETs).
const REGION = Deno.env.get("B2_REGION") || "us-east-005";
const ENDPOINT = `https://${Deno.env.get("B2_ENDPOINT") || "s3.us-east-005.backblazeb2.com"}`;
const BUCKET = Deno.env.get("B2_BUCKET") || "panalink-media-storage";
// Max lifetime B2 allows for presigned URLs (7 days). Covers the cleanup window
// of stories (24h) and reels (7 days); chat/muro media is deleted on demand.
const GET_EXPIRES_IN = 604800;

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
      console.error("Missing B2 credentials");
      return Response.json({ error: "B2 storage is not configured" }, { status: 503 });
    }

    let body: { fileName?: string; mimeType?: string; size?: number; uploadType?: string };
    try {
      body = await req.json();
    } catch {
      return Response.json({ error: "Invalid JSON" }, { status: 400 });
    }

    const fileName = sanitizeFileName(body.fileName || "file.bin");
    const mimeType = body.mimeType || "application/octet-stream";
    const uploadType = sanitizeFileName(body.uploadType || "misc");
    const objectKey = `panalink/${uploadType}/${userId}/${crypto.randomUUID()}-${fileName}`;

    const s3 = new S3Client({
      region: REGION,
      endpoint: ENDPOINT,
      credentials: { accessKeyId: accessKey, secretAccessKey: secretKey },
      forcePathStyle: true,
      // B2's S3-compatible endpoint rejects the default CRC32 checksums the SDK
      // adds to presigned URLs; only compute them when the operation requires it.
      requestChecksumCalculation: "WHEN_REQUIRED",
      responseChecksumValidation: "WHEN_REQUIRED",
    });

    // Presigned PUT for the device upload.
    const putCommand = new PutObjectCommand({
      Bucket: BUCKET,
      Key: objectKey,
      ContentType: mimeType,
    });
    const uploadUrl = await getSignedUrl(s3, putCommand, { expiresIn: 900 });

    // Presigned GET for serving (long-lived; B2 private bucket has no public URL).
    const getCommand = new GetObjectCommand({
      Bucket: BUCKET,
      Key: objectKey,
    });
    const publicUrl = await getSignedUrl(s3, getCommand, { expiresIn: GET_EXPIRES_IN });

    return Response.json({
      uploadUrl,
      publicUrl,
      key: objectKey,
      bucket: BUCKET,
      region: REGION,
      expiresIn: 900,
      size: body.size || 0,
      mimeType,
    });
  },
};
