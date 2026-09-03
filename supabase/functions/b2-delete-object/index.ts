import { S3Client, DeleteObjectCommand } from "npm:@aws-sdk/client-s3@3.1117.0";

const REGION = Deno.env.get("B2_REGION") || "us-east-005";
const ENDPOINT = `https://${Deno.env.get("B2_ENDPOINT") || "s3.us-east-005.backblazeb2.com"}`;
const BUCKET = Deno.env.get("B2_BUCKET") || "panalink-media-storage";

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

    const userId = getUserId(req);
    if (!userId) {
      return Response.json({ error: "Unauthorized" }, { status: 401 });
    }

    const accessKey = Deno.env.get("B2_KEY_ID");
    const secretKey = Deno.env.get("B2_APP_KEY");
    if (!accessKey || !secretKey) {
      return Response.json({ error: "B2 storage is not configured" }, { status: 503 });
    }

    let body: { key?: string };
    try {
      body = await req.json();
    } catch {
      return Response.json({ error: "Invalid JSON" }, { status: 400 });
    }

    const key = (body.key || "").trim();
    if (!key) {
      return Response.json({ error: "Missing key" }, { status: 400 });
    }

    // Only allow deleting objects under the requesting user's own prefix.
    if (!key.startsWith(`panalink/`) || !key.includes(`/${userId}/`)) {
      return Response.json({ error: "Forbidden: key does not belong to user" }, { status: 403 });
    }

    const s3 = new S3Client({
      region: REGION,
      endpoint: ENDPOINT,
      credentials: { accessKeyId: accessKey, secretAccessKey: secretKey },
      forcePathStyle: true,
    });

    try {
      await s3.send(new DeleteObjectCommand({ Bucket: BUCKET, Key: key }));
      return Response.json({ ok: true, key });
    } catch (e) {
      console.error("B2 delete failed", e);
      return Response.json({ error: "Delete failed" }, { status: 500 });
    }
  },
};
