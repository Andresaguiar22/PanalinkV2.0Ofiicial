-- B2 media storage + automatic cleanup
--
-- Introduces a deletion queue so that media hosted in Backblaze B2 is purged
-- from storage when the referencing row is gone. Postgres cannot delete B2
-- objects directly, so triggers enqueue URLs here and a cron drains the queue
-- through the b2-delete-batch edge function (auth via private.get_edge_secret).
--
-- Cleanup policy:
--   * stories (social.user_stories) -> when expires_at passes (24h).
--   * reels (social.user_reels)      -> older than 7 days.
--   * chat media (thread_messages)   -> when the message is deleted for
--     everyone, OR when every participant of the chat/thread has soft-deleted
--     it (user_deleted_messages). Protects the other party (WhatsApp-style).
--   * muro posts (public.posts)      -> when the post row is deleted.

-- =============================================================
-- 1. Deletion queue
-- =============================================================
create table if not exists public.media_deletion_queue (
    id          uuid primary key default gen_random_uuid(),
    storage_url text not null,
    reason      text not null,
    ref_id      uuid,
    created_at  timestamptz not null default now(),
    deleted_at  timestamptz,
    attempts    integer not null default 0
);

-- Service role (cron) can manage the queue; users never touch it directly.
alter table public.media_deletion_queue enable row level security;
drop policy if exists media_deletion_queue_service_all on public.media_deletion_queue;
create policy media_deletion_queue_service_all
    on public.media_deletion_queue for all
    to service_role using (true) with check (true);

create index if not exists idx_media_deletion_queue_pending
    on public.media_deletion_queue (created_at)
    where deleted_at is null;

-- =============================================================
-- 2. Helper: enqueue a media URL (skip null / empty / local / supabase)
-- =============================================================
create or replace function public.enqueue_media_deletion(
    p_storage_url text,
    p_reason text,
    p_ref_id uuid default null
) returns void
language sql security definer
set search_path to 'pg_catalog', 'public'
as $$
    insert into public.media_deletion_queue (storage_url, reason, ref_id)
    select p_storage_url, p_reason, p_ref_id
    where p_storage_url is not null
      and p_storage_url <> ''
      and p_storage_url not like 'content://%'
      and p_storage_url not like 'file://%'
      and p_storage_url not like '/%'
      and p_storage_url not like '%.supabase.co%';
$$;

-- =============================================================
-- 3. Stories cleanup (24h) — social.user_stories
--    The pre-existing cleanup_expired_stories() drains social.user_statuses
--    (presence rows); user_stories holds the actual media + expires_at, so we
--    add a parallel cleanup that also enqueues the B2 files.
-- =============================================================
create or replace function social.cleanup_expired_story_media(p_batch_size int default 5000)
returns void
language plpgsql security definer
set search_path to 'pg_catalog', 'public', 'social'
as $$
begin
    -- Enqueue media URLs of expired stories, then delete the rows. The data-modifying
    -- CTE (DELETE ... RETURNING) is referenced by the INSERT so both execute together.
    with picked as (
        select id from social.user_stories
        where expires_at is not null and expires_at <= now()
        order by expires_at asc limit p_batch_size
    ),
    deleted as (
        delete from social.user_stories s using picked p where s.id = p.id
        returning s.id, s.media_url, s.thumbnail_url, s.audio_url
    )
    insert into public.media_deletion_queue (storage_url, reason, ref_id)
    select v.url, 'story_expired', d.id
    from deleted d
    cross join lateral (values (d.media_url), (d.thumbnail_url), (d.audio_url)) as v(url)
    where v.url is not null
      and v.url <> ''
      and v.url not like 'content://%'
      and v.url not like 'file://%'
      and v.url not like '/%'
      and v.url not like '%.supabase.co%';
end;
$$;

-- =============================================================
-- 4. Reels cleanup (7 days) — social.user_reels
-- =============================================================
create or replace function social.cleanup_old_reels_media(p_max_age_days int default 7, p_batch_size int default 500)
returns void
language plpgsql security definer
set search_path to 'pg_catalog', 'public', 'social'
as $$
begin
    with picked as (
        select id from social.user_reels
        where created_at < (now() - (p_max_age_days || ' days')::interval)
        order by created_at asc limit p_batch_size
    ),
    deleted as (
        delete from social.user_reels r using picked p where r.id = p.id
        returning r.id, r.media_url, r.thumbnail_url, r.audio_url
    )
    insert into public.media_deletion_queue (storage_url, reason, ref_id)
    select v.url, 'reel_expired', d.id
    from deleted d
    cross join lateral (values (d.media_url), (d.thumbnail_url), (d.audio_url)) as v(url)
    where v.url is not null
      and v.url <> ''
      and v.url not like 'content://%'
      and v.url not like 'file://%'
      and v.url not like '/%'
      and v.url not like '%.supabase.co%';
end;
$$;

-- =============================================================
-- 5. Muro posts cleanup — when a post row is deleted
-- =============================================================
create or replace function public.enqueue_post_media_on_delete()
returns trigger
language plpgsql security definer
set search_path to 'pg_catalog', 'public'
as $$
declare
    url_item text;
begin
    if tg_op = 'DELETE' and old.media_urls is not null then
        -- media_urls is a jsonb array of URLs
        for url_item in select jsonb_array_elements_text(old.media_urls) loop
            perform enqueue_media_deletion(url_item, 'post_deleted', old.id);
        end loop;
    end if;
    return old;
end;
$$;

drop trigger if exists trg_enqueue_post_media_on_delete on public.posts;
create trigger trg_enqueue_post_media_on_delete
    after delete on public.posts
    for each row execute function public.enqueue_post_media_on_delete();

-- =============================================================
-- 6. Chat media cleanup — "both deleted" / "delete for everyone"
-- =============================================================

-- 6a. When a message is marked deleted_for_everyone (is_deleted=true),
--     enqueue its media immediately — nobody sees it anymore.
create or replace function public.enqueue_chat_media_on_delete_everyone()
returns trigger
language plpgsql security definer
set search_path to 'pg_catalog', 'public'
as $$
begin
    if new.is_deleted = true and coalesce(old.is_deleted, false) = false then
        perform enqueue_media_deletion(new.media_url, 'chat_deleted_everyone', new.id);
        perform enqueue_media_deletion(new.thumbnail_url, 'chat_deleted_everyone', new.id);
        perform enqueue_media_deletion(new.audio_url, 'chat_deleted_everyone', new.id);
        if new.media_urls is not null then
            perform enqueue_media_deletion(url_text, 'chat_deleted_everyone', new.id)
            from jsonb_array_elements_text(new.media_urls) as t(url_text)
            where url_text is not null;
        end if;
    end if;
    return new;
end;
$$;

drop trigger if exists trg_chat_media_on_delete_everyone on public.thread_messages;
create trigger trg_chat_media_on_delete_everyone
    after update of is_deleted on public.thread_messages
    for each row execute function public.enqueue_chat_media_on_delete_everyone();

-- 6b. When a user soft-deletes a message (user_deleted_messages INSERT),
--     check whether ALL participants of the chat/thread have now deleted it.
--     Only then enqueue the media (protects the other party — WhatsApp-style).
create or replace function public.enqueue_chat_media_when_all_deleted()
returns trigger
language plpgsql security definer
set search_path to 'pg_catalog', 'public'
as $$
declare
    v_message_id uuid := new.message_id;
    v_thread_id  uuid;
    v_chat_id    uuid;
    v_total_participants int;
    v_deleted_count int;
    v_msg record;
begin
    select thread_id, chat_id, media_url, thumbnail_url, audio_url, media_urls
      into v_msg.thread_id, v_msg.chat_id, v_msg.media_url, v_msg.thumbnail_url, v_msg.audio_url, v_msg.media_urls
      from public.thread_messages where id = v_message_id;

    if not found then return new; end if;

    if v_msg.thread_id is not null then
        -- 1:1 thread: exactly 2 participants.
        select count(*) into v_total_participants
          from public.one_to_one_threads t
         where t.id = v_msg.thread_id
           and t.user_a is not null and t.user_b is not null;
        -- one_to_one_threads has one row per thread -> 2 members.
        if v_total_participants = 0 then
            v_total_participants := 2;
        else
            v_total_participants := 2;
        end if;
    elsif v_msg.chat_id is not null then
        -- group chat: count active participants.
        select count(*) into v_total_participants
          from public.chat_participants cp
         where cp.chat_id = v_msg.chat_id
           and cp.left_at is null;
    else
        return new;
    end if;

    select count(distinct user_id) into v_deleted_count
      from public.user_deleted_messages
     where message_id = v_message_id;

    if v_deleted_count >= v_total_participants then
        perform enqueue_media_deletion(v_msg.media_url, 'chat_all_deleted', v_message_id);
        perform enqueue_media_deletion(v_msg.thumbnail_url, 'chat_all_deleted', v_message_id);
        perform enqueue_media_deletion(v_msg.audio_url, 'chat_all_deleted', v_message_id);
        if v_msg.media_urls is not null then
            perform enqueue_media_deletion(url_text, 'chat_all_deleted', v_message_id)
              from jsonb_array_elements_text(v_msg.media_urls) as t(url_text)
             where url_text is not null;
        end if;
    end if;

    return new;
end;
$$;

drop trigger if exists trg_chat_media_when_all_deleted on public.user_deleted_messages;
create trigger trg_chat_media_when_all_deleted
    after insert on public.user_deleted_messages
    for each row execute function public.enqueue_chat_media_when_all_deleted();

-- =============================================================
-- 7. Cron: drain the queue via the b2-delete-batch edge function.
--    Runs every 15 minutes; dispatches pending URLs in batches of 50.
-- =============================================================
create or replace function public.drain_media_deletion_queue(p_batch_size int default 50)
returns void
language plpgsql security definer
set search_path to 'pg_catalog', 'public', 'net'
as $$
declare
    v_url_list jsonb;
    v_secret text;
    v_project_url text;
    v_request_id bigint;
begin
    select coalesce(private.get_edge_secret(), '') into v_secret;
    if v_secret = '' then
        raise log 'drain_media_deletion_queue: edge secret missing; skipping';
        return;
    end if;

    -- Build the batch of pending URLs.
    select coalesce(jsonb_agg(storage_url), '[]'::jsonb) into v_url_list
      from (
        select storage_url
          from public.media_deletion_queue
         where deleted_at is null
         order by created_at asc
         limit p_batch_size
      ) sub;

    if jsonb_array_length(v_url_list) = 0 then
        return;
    end if;

    v_project_url := current_setting('app.project_url', true);
    if v_project_url is null or v_project_url = '' then
        v_project_url := 'https://tivqjfgjdxgzicrridaz.supabase.co';
    end if;

    -- Mark as processed optimistically (fire-and-forget; a leak is acceptable
    -- for a dev app — the edge function retries on the next run for 404s).
    update public.media_deletion_queue
       set deleted_at = now(), attempts = attempts + 1
     where id in (
        select id from public.media_deletion_queue
         where deleted_at is null
         order by created_at asc
         limit p_batch_size
     );

    begin
        v_request_id := net.http_post(
            url := v_project_url || '/functions/v1/b2-delete-batch',
            body := jsonb_build_object('urls', v_url_list),
            headers := jsonb_build_object(
                'Content-Type', 'application/json',
                'x-internal-secret', v_secret
            )
        );
    exception when others then
        raise log 'drain_media_deletion_queue: net.http_post failed: %', sqlerrm;
    end;
end;
$$;

-- =============================================================
-- 8. Schedule the cron jobs (idempotent: unschedule before re-creating)
-- =============================================================
do $cron_setup$
begin
    -- stories media cleanup every 15 min
    begin
        perform cron.unschedule('cleanup_expired_story_media_every_15m');
    exception when others then null;
    end;
    perform cron.schedule(
        'cleanup_expired_story_media_every_15m',
        '*/15 * * * *',
        $cron1$ select social.cleanup_expired_story_media(5000); $cron1$
    );

    -- reels cleanup daily at 03:30 UTC
    begin
        perform cron.unschedule('cleanup_old_reels_media_daily');
    exception when others then null;
    end;
    perform cron.schedule(
        'cleanup_old_reels_media_daily',
        '30 3 * * *',
        $cron2$ select social.cleanup_old_reels_media(7, 500); $cron2$
    );

    -- drain the B2 deletion queue every 15 min
    begin
        perform cron.unschedule('drain_media_deletion_queue_every_15m');
    exception when others then null;
    end;
    perform cron.schedule(
        'drain_media_deletion_queue_every_15m',
        '*/15 * * * *',
        $cron3$ select public.drain_media_deletion_queue(50); $cron3$
    );
end $cron_setup$;
