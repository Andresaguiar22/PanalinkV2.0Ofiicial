-- VCDN cleanup deletes for auto-expired media (stories 24h, reels 7d).
--
-- The B2 cleanup (20260827010000) enqueues B2 URLs de expired stories/reels; the
-- VCDN files (vcdn_video_id) are hosted on cdn.vcdn.me and are NOT B2 objects, so the
-- B2 drain skips them. These two functions are re-created to also fire
-- /functions/v1/vcdn-delete (x-internal-secret) for every vcdn_video_id before
-- the row is deleted (same drain pattern as b2-delete-batch).
--
-- Idempotent: create or replace, same signatures, cron schedules unchanged.

-- =============================================================
-- 1. Stories cleanup (24h)
-- =============================================================
create or replace function social.cleanup_expired_story_media(p_batch_size int default 5000)
returns void
language plpgsql security definer
set search_path to 'pg_catalog', 'public', 'social'
as $$
declare
    r record;
    v_secret text;
    v_project_url text;
begin
    drop table if exists _cleanup_story_vcdn;
    create temp table _cleanup_story_vcdn on commit drop as
        select id, vcdn_video_id
        from social.user_stories
        where expires_at is not null and expires_at <= now()
        order by expires_at asc limit p_batch_size;

    v_secret := coalesce(private.get_edge_secret(), '');
    v_project_url := current_setting('app.project_url', true);
    if v_project_url is null or v_project_url = '' then
        v_project_url := 'https://<secret-hidden>.supabase.co';
    end if;

    with picked as (select id from _cleanup_story_vcdn),
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

    if v_secret <> '' then
        for r in select distinct vcdn_video_id from _cleanup_story_vcdn where vcdn_video_id is not null loop
            perform net.http_post(
                url := v_project_url || '/functions/v1/vcdn-delete',
                body := jsonb_build_object('videoId', r.vcdn_video_id),
                headers := jsonb_build_object('Content-Type', 'application/json', 'x-internal-secret', v_secret)
            );
        end loop;
    end if;

    drop table if exists _cleanup_story_vcdn;
end;
$$;

-- =============================================================
-- 2. Reels cleanup (older than 7 days)
-- =============================================================
create or replace function social.cleanup_old_reels_media(p_max_age_days int default 7, p_batch_size int default 500)
returns void
language plpgsql security definer
set search_path to 'pg_catalog', 'public', 'social'
as $$
declare
    r record;
    v_secret text;
    v_project_url text;
begin
    drop table if exists _cleanup_reel_vcdn;
    create temp table _cleanup_reel_vcdn on commit drop as
        select id, vcdn_video_id
        from social.user_reels
        where created_at < (now() - (p_max_age_days || ' days')::interval)
        order by created_at asc limit p_batch_size;

    v_secret := coalesce(private.get_edge_secret(), '');
    v_project_url := current_setting('app.project_url', true);
    if v_project_url is null or v_project_url = '' then
        v_project_url := 'https://<secret-hidden>.supabase.co';
    end if;

    with picked as (select id from _cleanup_reel_vcdn),
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

    if v_secret <> '' then
        for r in select distinct vcdn_video_id from _cleanup_reel_vcdn where vcdn_video_id is not null loop
            perform net.http_post(
                url := v_project_url || '/functions/v1/vcdn-delete',
                body := jsonb_build_object('videoId', r.vcdn_video_id),
                headers := jsonb_build_object('Content-Type', 'application/json', 'x-internal-secret', v_secret)
            );
        end loop;
    end if;

    drop table if exists _cleanup_reel_vcdn;
end;
$$;