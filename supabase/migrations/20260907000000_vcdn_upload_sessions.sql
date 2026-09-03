-- VCDN Upload Sessions table and transactional RPCs to guarantee real idempotency across
-- concurrent requests, retries, worker restarts, and scaled Edge Function instances.

CREATE TABLE IF NOT EXISTS public.vcdn_upload_sessions (
    id text PRIMARY KEY, -- Composite key: {user_id}:{stable_id}
    user_id text NOT NULL,
    stable_id text NOT NULL,
    upload_id text,
    video_id text,
    upload_url text,
    filename text,
    content_type text,
    size bigint,
    bytes_received bigint DEFAULT 0,
    status text DEFAULT 'claiming', -- 'claiming', 'initiated', 'uploading', 'completing', 'completed', 'ready', 'failed'
    poster_url text,
    ready boolean DEFAULT false,
    claimed_at timestamptz DEFAULT now(),
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now(),
    CONSTRAINT uq_vcdn_sessions_user_stable UNIQUE (user_id, stable_id)
);

CREATE INDEX IF NOT EXISTS idx_vcdn_sessions_user_stable ON public.vcdn_upload_sessions (user_id, stable_id);
CREATE INDEX IF NOT EXISTS idx_vcdn_sessions_upload_id ON public.vcdn_upload_sessions (upload_id);

ALTER TABLE public.vcdn_upload_sessions ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own upload sessions"
    ON public.vcdn_upload_sessions
    FOR SELECT
    USING (auth.uid()::text = user_id);

-- 1. Atomic claim function: guarantees only ONE caller obtains PROCEED_INIT
CREATE OR REPLACE FUNCTION public.claim_vcdn_upload_session(
    p_user_id text,
    p_stable_id text,
    p_filename text DEFAULT NULL,
    p_content_type text DEFAULT NULL,
    p_size bigint DEFAULT 0
)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_session public.vcdn_upload_sessions%ROWTYPE;
    v_id text := p_user_id || ':' || p_stable_id;
BEGIN
    -- Check if session already exists
    SELECT * INTO v_session
    FROM public.vcdn_upload_sessions
    WHERE user_id = p_user_id AND stable_id = p_stable_id
    FOR UPDATE;

    IF FOUND THEN
        -- If session has an active upload_id / video_id, reuse it
        IF v_session.upload_id IS NOT NULL AND v_session.video_id IS NOT NULL THEN
            RETURN json_build_object(
                'action', 'REUSE',
                'uploadId', v_session.upload_id,
                'videoId', v_session.video_id,
                'uploadUrl', v_session.upload_url,
                'bytesReceived', v_session.bytes_received,
                'status', v_session.status,
                'ready', v_session.ready,
                'posterUrl', v_session.poster_url
            );
        END IF;

        -- If someone claimed it less than 15 seconds ago and is currently calling upstream VCDN
        IF v_session.claimed_at > now() - interval '15 seconds' THEN
            RETURN json_build_object(
                'action', 'WAIT_CLAIM',
                'status', v_session.status
            );
        END IF;

        -- Previous claim expired/failed without populating upload_id. Reclaim.
        UPDATE public.vcdn_upload_sessions
        SET claimed_at = now(),
            status = 'claiming',
            filename = COALESCE(p_filename, filename),
            content_type = COALESCE(p_content_type, content_type),
            size = COALESCE(p_size, size),
            updated_at = now()
        WHERE id = v_id
        RETURNING * INTO v_session;

        RETURN json_build_object(
            'action', 'PROCEED_INIT'
        );
    END IF;

    -- No session exists. Insert new claim atomically.
    BEGIN
        INSERT INTO public.vcdn_upload_sessions (
            id, user_id, stable_id, filename, content_type, size, status, claimed_at
        ) VALUES (
            v_id, p_user_id, p_stable_id, p_filename, p_content_type, p_size, 'claiming', now()
        )
        RETURNING * INTO v_session;

        RETURN json_build_object(
            'action', 'PROCEED_INIT'
        );
    EXCEPTION WHEN unique_violation THEN
        -- Caught concurrent insert collision; re-read existing session
        SELECT * INTO v_session
        FROM public.vcdn_upload_sessions
        WHERE user_id = p_user_id AND stable_id = p_stable_id;

        IF v_session.upload_id IS NOT NULL AND v_session.video_id IS NOT NULL THEN
            RETURN json_build_object(
                'action', 'REUSE',
                'uploadId', v_session.upload_id,
                'videoId', v_session.video_id,
                'uploadUrl', v_session.upload_url,
                'bytesReceived', v_session.bytes_received,
                'status', v_session.status,
                'ready', v_session.ready,
                'posterUrl', v_session.poster_url
            );
        ELSE
            RETURN json_build_object(
                'action', 'WAIT_CLAIM',
                'status', v_session.status
            );
        END IF;
    END;
END;
$$;

-- 2. Populate session with upstream VCDN response
CREATE OR REPLACE FUNCTION public.populate_vcdn_upload_session(
    p_user_id text,
    p_stable_id text,
    p_upload_id text,
    p_video_id text,
    p_upload_url text DEFAULT NULL
)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_session public.vcdn_upload_sessions%ROWTYPE;
BEGIN
    UPDATE public.vcdn_upload_sessions
    SET upload_id = p_upload_id,
        video_id = p_video_id,
        upload_url = p_upload_url,
        status = 'initiated',
        bytes_received = 0,
        updated_at = now()
    WHERE user_id = p_user_id AND stable_id = p_stable_id
    RETURNING * INTO v_session;

    IF FOUND THEN
        RETURN json_build_object(
            'success', true,
            'uploadId', v_session.upload_id,
            'videoId', v_session.video_id
        );
    ELSE
        RETURN json_build_object('success', false, 'error', 'session_not_found');
    END IF;
END;
$$;

-- 3. Fail/release claim in case upstream VCDN init failed
CREATE OR REPLACE FUNCTION public.fail_vcdn_upload_session(
    p_user_id text,
    p_stable_id text,
    p_error text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    UPDATE public.vcdn_upload_sessions
    SET status = 'failed',
        updated_at = now()
    WHERE user_id = p_user_id AND stable_id = p_stable_id AND upload_id IS NULL;
END;
$$;

-- 4. Atomic chunk bytesReceived update
CREATE OR REPLACE FUNCTION public.update_vcdn_upload_bytes(
    p_upload_id text,
    p_bytes_received bigint
)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_session public.vcdn_upload_sessions%ROWTYPE;
BEGIN
    UPDATE public.vcdn_upload_sessions
    SET bytes_received = GREATEST(COALESCE(bytes_received, 0), p_bytes_received),
        status = CASE WHEN status = 'completed' OR status = 'ready' THEN status ELSE 'uploading' END,
        updated_at = now()
    WHERE upload_id = p_upload_id
    RETURNING * INTO v_session;

    IF FOUND THEN
        RETURN json_build_object(
            'bytesReceived', v_session.bytes_received,
            'status', v_session.status
        );
    ELSE
        RETURN json_build_object('bytesReceived', p_bytes_received, 'status', 'uploading');
    END IF;
END;
$$;

-- 5. Atomic complete claim
CREATE OR REPLACE FUNCTION public.claim_vcdn_complete(
    p_upload_id text
)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_session public.vcdn_upload_sessions%ROWTYPE;
BEGIN
    SELECT * INTO v_session
    FROM public.vcdn_upload_sessions
    WHERE upload_id = p_upload_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RETURN json_build_object('action', 'PROCEED_COMPLETE');
    END IF;

    IF v_session.status = 'completed' OR v_session.status = 'ready' OR v_session.ready THEN
        RETURN json_build_object('action', 'ALREADY_COMPLETED', 'videoId', v_session.video_id);
    END IF;

    IF v_session.status = 'completing' THEN
        RETURN json_build_object('action', 'ALREADY_COMPLETING', 'videoId', v_session.video_id);
    END IF;

    UPDATE public.vcdn_upload_sessions
    SET status = 'completing',
        updated_at = now()
    WHERE upload_id = p_upload_id;

    RETURN json_build_object('action', 'PROCEED_COMPLETE', 'videoId', v_session.video_id);
END;
$$;

-- 6. Finalize session
CREATE OR REPLACE FUNCTION public.finalize_vcdn_session(
    p_upload_id text,
    p_ready boolean DEFAULT false,
    p_poster_url text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    UPDATE public.vcdn_upload_sessions
    SET status = CASE WHEN p_ready THEN 'ready' ELSE 'completed' END,
        ready = p_ready,
        poster_url = COALESCE(p_poster_url, poster_url),
        updated_at = now()
    WHERE upload_id = p_upload_id;
END;
$$;
