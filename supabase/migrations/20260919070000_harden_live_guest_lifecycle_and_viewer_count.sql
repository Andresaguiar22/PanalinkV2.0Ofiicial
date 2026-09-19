-- Harden Live guest lifecycle and viewer-count authority.
-- Guest requests are PENDING; host invitations are INVITED.
-- Only the host may persist the authoritative viewer count.

drop policy if exists "Guest can update own invitation status" on public.live_guests;
drop policy if exists "Guest can insert own join request" on public.live_guests;
drop policy if exists "Guest can accept own invitation" on public.live_guests;
drop policy if exists "Guest can reject own invitation" on public.live_guests;
drop policy if exists "Guest can disconnect own session" on public.live_guests;

create policy "Guest can insert own join request"
on public.live_guests for insert to authenticated
with check (
  guest_user_id = auth.uid()::text
  and status = 'PENDING'
  and exists (
    select 1 from public.live_streams s
    where s.id = stream_id and s.status = 'LIVE' and s.host_id <> auth.uid()
  )
);

create policy "Guest can accept own invitation"
on public.live_guests for update to authenticated
using (guest_user_id = auth.uid()::text and status = 'INVITED')
with check (guest_user_id = auth.uid()::text and status = 'ACCEPTED');

create policy "Guest can reject own invitation"
on public.live_guests for update to authenticated
using (guest_user_id = auth.uid()::text and status in ('INVITED', 'PENDING'))
with check (guest_user_id = auth.uid()::text and status = 'REJECTED');

create policy "Guest can disconnect own session"
on public.live_guests for update to authenticated
using (guest_user_id = auth.uid()::text and status in ('ACCEPTED', 'CONNECTED'))
with check (guest_user_id = auth.uid()::text and status = 'DISCONNECTED');

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.live_guests'::regclass
      and conname = 'live_guests_status_check'
  ) then
    alter table public.live_guests add constraint live_guests_status_check
      check (status in ('PENDING','INVITED','ACCEPTED','REJECTED','ACTIVE','CONNECTED','DISCONNECTED','REMOVED'));
  end if;
end $$;

create or replace function public.live_set_viewer_count(p_stream_id uuid, p_count integer)
returns void language plpgsql security definer set search_path = ''
as $$
declare
  v_uid uuid := auth.uid();
  v_count integer := greatest(0, coalesce(p_count, 0));
  v_host uuid;
begin
  if v_uid is null then raise exception 'not_authenticated'; end if;

  select host_id into v_host from public.live_streams
   where id = p_stream_id and status = 'LIVE';

  if v_host is null then raise exception 'stream_not_live'; end if;
  if v_host <> v_uid then raise exception 'not_stream_host'; end if;

  insert into public.live_stream_stats (stream_id, viewer_count, updated_at)
  values (p_stream_id, v_count, now())
  on conflict (stream_id) do update
    set viewer_count = excluded.viewer_count, updated_at = now();
end;
$$;

revoke all on function public.live_set_viewer_count(uuid, integer) from public, anon;
grant execute on function public.live_set_viewer_count(uuid, integer) to authenticated;
