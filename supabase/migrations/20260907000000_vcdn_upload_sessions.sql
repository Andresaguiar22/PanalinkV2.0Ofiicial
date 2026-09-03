-- VCDN Upload Sessions table to guarantee real idempotency across retries, worker restarts, and scaled Edge Functions.
CREATE TABLE IF NOT EXISTS public.vcdn_upload_sessions (
    id text PRIMARY KEY, -- Composite key: {user_id}:{stable_id}
    user_id text NOT NULL,
    stable_id text NOT NULL,
    upload_id text NOT NULL,
    video_id text NOT NULL,
    upload_url text,
    filename text,
    content_type text,
    size bigint,
    bytes_received bigint DEFAULT 0,
    status text DEFAULT 'initiated',
    poster_url text,
    ready boolean DEFAULT false,
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vcdn_sessions_user_stable ON public.vcdn_upload_sessions (user_id, stable_id);

ALTER TABLE public.vcdn_upload_sessions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own upload sessions"
    ON public.vcdn_upload_sessions
    FOR SELECT
    USING (auth.uid()::text = user_id);
