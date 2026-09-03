import { S3Client, DeleteObjectsCommand } from "npm:@aws-sdk/client-s3@3.1117.0";

// Called by the Postgres cron (pg_net) that drains media_deletion_queue.
// Auth: internal edge secret EDGE_INTERNAL_SECRET (must match private.get_edge_secret()).
// Accepts full B2 URLs (as stored in media_url columns); non-B2 URLs are skipped
// (legacy Sufy/CDN media is left alone — only B2-hosted objects are deleted here).
const REGION = Deno.env.get("B2_REGION") || "us-east-005";
const ENDPOINT = `https://${Deno.env.get("B2_ENDPOINT") || "s3.us-east-005.backblazeb2.com"}`;
const BUCKET = Deno.env.get("B2_BUCKET") || "panalink-media-storage";

// Extract the S3 object key from a B2 URL. Path-style URLs look like:
//   https://s3.us-east-005.backblazeb2.com/panalink-media-storage/panalink/REEL/uid/x.mp4?X-Amz-...
// The key is everything after the bucket name in the pathname.
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

    const internalSecret = Deno.env.get("EDGE_INTERNAL_SECRET");
    const provided = req.headers.get("x-internal-secret") || "";
    if (!internalSecret || provided !== internalSecret) {
      return Response.json({ error: "Unauthorized" }, { status: 401 });
    }

    const accessKey = Deno.env.get("B2_KEY_ID");
    const secretKey = Deno.env.get("B2_APP_KEY");
    if (!accessKey || !secretKey) {
      return Response.json({ error: "B2 storage is not configured" }, { status: 503 });
    }

    let body: { urls?: string[] };
    try {
      body = await req.json();
    } catch {
      return Response.json({ error: "Invalid JSON" }, { status: 400 });
    }

    const rawUrls = Array.isArray(body.urls) ? body.urls.filter((u) => typeof u === "string" && u.trim()) : [];
    if (rawUrls.length === 0) {
      return Response.json({ ok: true, deleted: [] });
    }

    // Map URLs to B2 keys; drop non-B2 URLs.
    const keyByUrl = new Map<string, string>();
    for (const url of rawUrls) {
      const key = urlToKey(url);
      if (key) keyByUrl.set(url, key);
    }
    if (keyByUrl.size === 0) {
      return Response.json({ ok: true, deleted: [], skipped: rawUrls.length });
    }

    const s3 = new S3Client({
      region: REGION,
      endpoint: ENDPOINT,
      credentials: { accessKeyId: accessKey, secretAccessKey: secretKey },
      forcePathStyle: true,
    });

    // S3 DeleteObjects accepts up to 1000 keys per call.
    const keys = Array.from(keyByUrl.values());
    const deleted: string[] = [];
    const errors: { key: string; error: string }[] = [];
    for (let i = 0; i < keys.length; i += 1000) {
      const chunk = keys.slice(i, i + 1000).map((k) => ({ Key: k }));
      try {
        const out = await s3.send(
          new DeleteObjectsCommand({ Bucket: BUCKET, Delete: { Objects: chunk, Quiet: false } }),
        );
        for (const d of out.Deleted || []) deleted.push(d.Key || "");
        for (const e of out.Errors || []) errors.push({ key: e.Key || "", error: e.Message || "unknown" });
      } catch (e) {
        for (const k of keys.slice(i, i + 1000)) errors.push({ key: k, error: String(e) });
      }
    }

    return Response.json({ ok: true, deleted, errors, processed: keys.length, skipped: rawUrls.length - keys.length });
  },
};
