-- Presence online/offline realtime fix.
-- Applied to prod via Management API (2026-09-18). This file documents the DDL
-- so a fresh clone produces the same schema.

-- 1) Remove the broken broadcast trigger function and recreate it against the
--    REAL columns of public.user_presence (the previous body referenced
--    NEW.computed_status which does not exist -> every INSERT/UPDATE failed
--    with 42703 "record new has no field computed_status", so presence never
--    persisted on this backend).
create or replace function public.presence_realtime_broadcast_trigger()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  uid uuid;
begin
  uid := coalesce(new.user_id, old.user_id);
  perform realtime.broadcast_changes(
    'presence:' || uid::text,
    tg_op,
    tg_op,
    tg_table_name,
    tg_table_schema,
    new,
    old
  );
  return coalesce(new, old);
end;
$function$;

-- 2) Let other authenticated users read everyone's presence rows so the
--    realtime postgres_changes stream can deliver ONLINE/AWAY updates to the
--    clients that are viewing them. The old policy "user_presence: select own"
--    only allowed SELECT of your own row, which made cross-user presence
--    invisible AND suppressed the postgres_changes records for others.
drop policy if exists "user_presence: select visible" on public.user_presence;
create policy "user_presence: select visible"
on public.user_presence for select
to authenticated
using (true);

-- 3) Publish presence changes over the standard realtime Postgres Changes feed
--    (topic realtime:public:user_presence), the same channel the Android app
--    subscribes to for messages/threads/etc.
do $$
begin
  if not exists (
    select 1
    from pg_publication_tables
    where pubname = 'supabase_realtime'
      and schemaname = 'public'
      and tablename = 'user_presence'
  ) then
    alter publication supabase_realtime add table public.user_presence;
  end if;
end $$;

-- 4) Full replica identity so UPDATE payloads include the full row (old + new)
--    in postgres_changes.
alter table public.user_presence replica identity full;

-- 5) The DB enum presence_status_type lacked 'away', which the app uses when the
--    user backgrounds the device (PresenceLifecycleObserver). Add it so AWAY
--    writes persist instead of failing with invalid input value for enum.
do $$
begin
  if not exists (
    select 1 from pg_enum e
    join pg_type t on t.oid = e.enumtypid
    where t.typname = 'presence_status_type' and e.enumlabel = 'away'
  ) then
    alter type public.presence_status_type add value 'away';
  end if;
end $$;