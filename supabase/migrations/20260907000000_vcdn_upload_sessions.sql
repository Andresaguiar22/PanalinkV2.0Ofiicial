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
    claim_owner_token text,
    claim_heartbeat_at timestamptz DEFAULT now(),
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now(),
    CONSTRAINT uq_vcdn_sessions_user_stable UNIQUE (user_id, stable_id)
);

CREATE INDEX IF NOT EXISTS idx_vcdn_sessions_user_stable ON public.vcdn_upload_sessions (user_id, stable_id);
CREATE INDEX IF NOT EXISTS idx_vcdn_sessions_upload_id ON public.vcdn_upload_sessions (upload_id);
CREATE INDEX IF NOT EXISTS idx_vcdn_sessions_video_id ON public.vcdn_upload_sessions (video_id);

ALTER TABLE public.vcdn_upload_sessions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view own upload sessions" ON public.vcdn_upload_sessions;
CREATE POLICY "Users can view own upload sessions"
    ON public.vcdn_upload_sessions
    FOR SELECT
    USING (auth.uid()::text = user_id);

-- Revoke default execution on public schema functions from anon and public by default
-- Access will be explicitly granted to authenticated and service_role.

-- 1. Atomic claim function: guarantees only ONE caller obtains PROCEED_INIT with a lease token.
CREATE OR REPLACE FUNCTION public.claim_vcdn_upload_session(
    p_user_id text,
    p_stable_id text,
    p_owner_token text,
    p_filename text DEFAULT NULL,
    p_content_type text DEFAULT NULL,
    p_size bigint DEFAULT 0
)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller_id text := auth.uid()::text;
    v_session public.vcdn_upload_sessions%ROWTYPE;
    v_id text;
BEGIN
    -- Security check: when called from authenticated context, ensure caller matches p_user_id
    IF v_caller_id IS NOT NULL AND v_caller_id <> '' AND v_caller_id <> p_user_id THEN
        RAISE EXCEPTION 'Unauthorized user claim';
    END IF;

    IF p_user_id IS NULL OR p_user_id = '' OR p_stable_id IS NULL OR p_stable_id = '' THEN
        RAISE EXCEPTION 'Invalid parameters';
    END IF;

    v_id := p_user_id || ':' || p_stable_id;

    -- Check if session already exists
    SELECT * INTO v_session
    FROM public.vcdn_upload_sessions
    WHERE user_id = p_user_id AND stable_id = p_stable_id
    FOR UPDATE;

    IF FOUND THEN
        -- If session has an active upload_id / video_id, reuse it immediately
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

        -- If the same owner token already holds the claim, allow it to continue
        IF v_session.claim_owner_token = p_owner_token THEN
            UPDATE public.vcdn_upload_sessions
            SET claim_heartbeat_at = now(),
                updated_at = now()
            WHERE id = v_id;

            RETURN json_build_object(
                'action', 'PROCEED_INIT',
                'ownerToken', p_owner_token
            );
        END IF;

        -- If claimed recently (<60s heartbeat) by another worker/instance, instruct caller to wait
        IF v_session.claim_heartbeat_at > now() - interval '60 seconds' THEN
            RETURN json_build_object(
                'action', 'WAIT_CLAIM',
                'status', v_session.status
            );
        END IF;

        -- Claim is orphaned (>60s without heartbeat and no upload_id recorded).
        -- Take over claim with new owner token.
        UPDATE public.vcdn_upload_sessions
        SET claim_owner_token = p_owner_token,
            claim_heartbeat_at = now(),
            status = 'claiming',
            filename = COALESCE(p_filename, filename),
            content_type = COALESCE(p_content_type, content_type),
            size = COALESCE(p_size, size),
            updated_at = now()
        WHERE id = v_id
        RETURNING * INTO v_session;

        RETURN json_build_object(
            'action', 'PROCEED_INIT',
            'ownerToken', p_owner_token
        );
    END IF;

    -- No session exists. Insert new claim atomically.
    BEGIN
        INSERT INTO public.vcdn_upload_sessions (
            id, user_id, stable_id, filename, content_type, size, status,
            claim_owner_token, claim_heartbeat_at
        ) VALUES (
            v_id, p_user_id, p_stable_id, p_filename, p_content_type, p_size, 'claiming',
            p_owner_token, now()
        )
        RETURNING * INTO v_session;

        RETURN json_build_object(
            'action', 'PROCEED_INIT',
            'ownerToken', p_owner_token
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

-- 2. Populate session with upstream VCDN response (requires owner token matching or empty claim)
CREATE OR REPLACE FUNCTION public.populate_vcdn_upload_session(
    p_user_id text,
    p_stable_id text,
    p_owner_token text,
    p_upload_id text,
    p_video_id text,
    p_upload_url text DEFAULT NULL
)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller_id text := auth.uid()::text;
    v_session public.vcdn_upload_sessions%ROWTYPE;
BEGIN
    IF v_caller_id IS NOT NULL AND v_caller_id <> '' AND v_caller_id <> p_user_id THEN
        RAISE EXCEPTION 'Unauthorized user populate';
    END IF;

    UPDATE public.vcdn_upload_sessions
    SET upload_id = p_upload_id,
        video_id = p_video_id,
        upload_url = p_upload_url,
        status = 'initiated',
        bytes_received = 0,
        updated_at = now()
    WHERE user_id = p_user_id 
      AND stable_id = p_stable_id
      AND (claim_owner_token = p_owner_token OR claim_owner_token IS NULL OR upload_id IS NULL)
    RETURNING * INTO v_session;

    IF FOUND THEN
        RETURN json_build_object(
            'success', true,
            'uploadId', v_session.upload_id,
            'videoId', v_session.video_id
        );
    ELSE
        -- Return current session state if another claim owner populated it
        SELECT * INTO v_session
        FROM public.vcdn_upload_sessions
        WHERE user_id = p_user_id AND stable_id = p_stable_id;

        IF FOUND AND v_session.upload_id IS NOT NULL THEN
            RETURN json_build_object(
                'success', true,
                'uploadId', v_session.upload_id,
                'videoId', v_session.video_id,
                'reusedExisting', true
            );
        END IF;

        RETURN json_build_object('success', false, 'error', 'session_not_found_or_token_mismatch');
    END IF;
END;
$$;

-- 3. Fail/release claim in case upstream VCDN init failed
CREATE OR REPLACE FUNCTION public.fail_vcdn_upload_session(
    p_user_id text,
    p_stable_id text,
    p_owner_token text,
    p_error text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller_id text := auth.uid()::text;
BEGIN
    IF v_caller_id IS NOT NULL AND v_caller_id <> '' AND v_caller_id <> p_user_id THEN
        RAISE EXCEPTION 'Unauthorized user fail';
    END IF;

    UPDATE public.vcdn_upload_sessions
    SET status = 'failed',
        claim_heartbeat_at = to_timestamp(0), -- immediately allow reclaim
        updated_at = now()
    WHERE user_id = p_user_id 
      AND stable_id = p_stable_id 
      AND claim_owner_token = p_owner_token
      AND upload_id IS NULL;
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
SET search_path = public, pg_temp
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
SET search_path = public, pg_temp
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

-- 6. Finalize session (by upload_id OR video_id OR stable identity)
CREATE OR REPLACE FUNCTION public.finalize_vcdn_session(
    p_upload_id text DEFAULT NULL,
    p_video_id text DEFAULT NULL,
    p_ready boolean DEFAULT false,
    p_poster_url text DEFAULT NULL
)
RETURNS json
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_session public.vcdn_upload_sessions%ROWTYPE;
BEGIN
    IF (p_upload_id IS NULL OR p_upload_id = '') AND (p_video_id IS NULL OR p_video_id = '') THEN
        RETURN json_build_object('success', false, 'error', 'missing_identifier');
    END IF;

    UPDATE public.vcdn_upload_sessions
    SET status = CASE WHEN p_ready THEN 'ready' ELSE 'completed' END,
        ready = p_ready,
        poster_url = COALESCE(p_poster_url, poster_url),
        updated_at = now()
    WHERE (p_upload_id IS NOT NULL AND p_upload_id <> '' AND upload_id = p_upload_id)
       OR (p_video_id IS NOT NULL AND p_video_id <> '' AND video_id = p_video_id)
    RETURNING * INTO v_session;

    IF FOUND THEN
        RETURN json_build_object(
            'success', true,
            'uploadId', v_session.upload_id,
            'videoId', v_session.video_id,
            'status', v_session.status,
            'ready', v_session.ready
        );
    ELSE
        RETURN json_build_object('success', false, 'error', 'session_not_found');
    END IF;
END;
$$;

-- 7. Reset failed complete claim so caller can safely retry
CREATE OR REPLACE FUNCTION public.fail_vcdn_complete_claim(
    p_upload_id text
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    UPDATE public.vcdn_upload_sessions
    SET status = 'uploading',
        updated_at = now()
    WHERE upload_id = p_upload_id AND status = 'completing';
END;
$$;

-- Permissions: Grant execution only to authenticated users and service_role
REVOKE EXECUTE ON FUNCTION public.claim_vcdn_upload_session(text, text, text, text, text, bigint) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.claim_vcdn_upload_session(text, text, text, text, text, bigint) TO authenticated, service_role;

REVOKE EXECUTE ON FUNCTION public.populate_vcdn_upload_session(text, text, text, text, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.populate_vcdn_upload_session(text, text, text, text, text, text) TO authenticated, service_role;

REVOKE EXECUTE ON FUNCTION public.fail_vcdn_upload_session(text, text, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.fail_vcdn_upload_session(text, text, text, text) TO authenticated, service_role;

REVOKE EXECUTE ON FUNCTION public.update_vcdn_upload_bytes(text, bigint) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.update_vcdn_upload_bytes(text, bigint) TO authenticated, service_role;

REVOKE EXECUTE ON FUNCTION public.claim_vcdn_complete(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.claim_vcdn_complete(text) TO authenticated, service_role;

REVOKE EXECUTE ON FUNCTION public.finalize_vcdn_session(text, text, boolean, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.finalize_vcdn_session(text, text, boolean, text) TO authenticated, service_role;

REVOKE EXECUTE ON FUNCTION public.fail_vcdn_complete_claim(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.fail_vcdn_complete_claim(text) TO authenticated, service_role;
