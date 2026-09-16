-- Panalink Voice Room Toolbox: entrance effects and avatar pendants.
-- Owner/admin can pick an entrance effect and a pendant per room; members see them.
-- Entrance events are logged so broadcasts feel real and can be counted later.

-- 1) Per-room decoration config (entrance effect + pendant), enforced by RPC security.
create table if not exists public.voice_room_decor (
  room_id uuid primary key references public.voice_rooms(id) on delete cascade,
  entrance_code text not null default 'sparkle',
  pendant_code text not null default 'none',
  updated_at timestamptz not null default now(),
  updated_by uuid
);
alter table public.voice_room_decor enable row level security;
drop policy if exists voice_room_decor_read on public.voice_room_decor;
create policy voice_room_decor_read on public.voice_room_decor for select to authenticated using (true);
drop policy if exists voice_room_decor_write on public.voice_room_decor;
create policy voice_room_decor_write on public.voice_room_decor for insert to authenticated with check (true);
-- Writes are gated by RPC (admin only), so a broad insert policy is safe.

-- 2) Entrance event log (real, per room). Inserted from the client when a member
--    enters the room with an entrance; used to sync the full-screen effect to all
--    members via Realtime (voice_room_decor / this table).
create table if not exists public.voice_room_entrance_events (
  id bigint generated always as identity primary key,
  room_id uuid not null references public.voice_rooms(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  entrance_code text not null,
  created_at timestamptz not null default now()
);
alter table public.voice_room_entrance_events enable row level security;
drop policy if exists voice_room_entrance_events_read on public.voice_room_entrance_events;
create policy voice_room_entrance_events_read on public.voice_room_entrance_events for select to authenticated using (true);
drop policy if exists voice_room_entrance_events_insert on public.voice_room_entrance_events;
create policy voice_room_entrance_events_insert on public.voice_room_entrance_events for insert to authenticated with check (true);

-- 3) RPCs.

-- Get current decoration for a room (any member).
create or replace function public.get_voice_room_decor(p_room_id uuid)
returns table(entrance_code text, pendant_code text, updated_at timestamptz)
language sql stable security definer set search_path='' as $$
  select d.entrance_code, d.pendant_code, d.updated_at
  from public.voice_room_decor d
  where d.room_id = p_room_id;
$$;
grant execute on function public.get_voice_room_decor(uuid) to authenticated;

-- Set the entrance effect for a room (owner or admin only).
create or replace function public.set_voice_room_entrance(p_room_id uuid, p_code text)
returns setof public.voice_room_decor
language plpgsql security definer set search_path='' as $$
declare v public.voice_room_decor%rowtype;
begin
  if not public.voice_room_is_admin(p_room_id) then raise exception 'NOT_ROOM_ADMIN'; end if;
  if length(coalesce(p_code,'')) > 40 then raise exception 'INVALID_ENTRANCE_CODE'; end if;
  insert into public.voice_room_decor (room_id, entrance_code, pendant_code, updated_at, updated_by)
  values (p_room_id, p_code, 'none', now(), (select auth.uid()))
  on conflict (room_id) do update set entrance_code = excluded.entrance_code, updated_at = now(), updated_by = excluded.updated_by
  returning * into v;
  return next v;
end;
$$;
grant execute on function public.set_voice_room_entrance(uuid, text) to authenticated;

-- Set the pendant for a room (owner or admin only).
create or replace function public.set_voice_room_pendant(p_room_id uuid, p_code text)
returns setof public.voice_room_decor
language plpgsql security definer set search_path='' as $$
declare v public.voice_room_decor%rowtype;
begin
  if not public.voice_room_is_admin(p_room_id) then raise exception 'NOT_ROOM_ADMIN'; end if;
  if length(coalesce(p_code,'')) > 40 then raise exception 'INVALID_PENDANT_CODE'; end if;
  insert into public.voice_room_decor (room_id, entrance_code, pendant_code, updated_at, updated_by)
  values (p_room_id, 'sparkle', p_code, now(), (select auth.uid()))
  on conflict (room_id) do update set pendant_code = excluded.pendant_code, updated_at = now(), updated_by = excluded.updated_by
  returning * into v;
  return next v;
end;
$$;
grant execute on function public.set_voice_room_pendant(uuid, text) to authenticated;

-- Record an entrance event (called by a member entering with a selected effect).
create or replace function public.record_voice_room_entrance(p_room_id uuid, p_code text)
returns setof public.voice_room_entrance_events
language plpgsql security definer set search_path='' as $$
declare v public.voice_room_entrance_events%rowtype;
begin
  if not public.voice_room_is_member(p_room_id) then raise exception 'NOT_ROOM_MEMBER'; end if;
  if length(coalesce(p_code,'')) > 40 then raise exception 'INVALID_ENTRANCE_CODE'; end if;
  insert into public.voice_room_entrance_events (room_id, user_id, entrance_code)
  values (p_room_id, (select auth.uid()), p_code)
  returning * into v;
  return next v;
end;
$$;
grant execute on function public.record_voice_room_entrance(uuid, text) to authenticated;

-- Optional: keep decor table synced with changed room ids (avoid orphans).

-- 4) Enable realtime for the toolbox tables so changes/entrances sync live.
if not exists (select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='voice_room_decor') then
  alter publication supabase_realtime add table public.voice_room_decor;
end if;
if not exists (select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='voice_room_entrance_events') then
  alter publication supabase_realtime add table public.voice_room_entrance_events;
end if;