-- VCDN video CDN: store a stable vcdn_video_id (+ public poster) per reel/story.
-- The signed HLS streamUrl expires, so we only persist the stable id and resolve a
-- fresh streamUrl at playback time (via the public BFF player-config endpoint).
-- media_url stays as-is for legacy/compatibility; for VCDN videos it holds the
-- pointer "vcdn://{videoId}" so existing players can route through VcdnUrlResolver.
-- Idempotent: safe to run multiple times.

ALTER TABLE social.user_reels
    ADD COLUMN IF NOT EXISTS vcdn_video_id text;
ALTER TABLE social.user_reels
    ADD COLUMN IF NOT EXISTS vcdn_poster_url text;

ALTER TABLE social.user_stories
    ADD COLUMN IF NOT EXISTS vcdn_video_id text;
ALTER TABLE social.user_stories
    ADD COLUMN IF NOT EXISTS vcdn_poster_url text;
