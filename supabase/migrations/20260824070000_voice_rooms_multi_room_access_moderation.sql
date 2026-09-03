-- Panalink Voice Rooms v2: many live rooms, public/private access, bans, admins and seat requests.
-- Independent from private chat tables.

alter table public.voice_rooms
  add column if not exists description text not null default '',
  add column if not exists cover_url text,
  add column if not exists category text not null default 'general',
  add column if not exists visibility text not null default 'public',
  add column if not exists is_locked boolean not null default false;

alter table public.voice_room_members drop constraint if exists voice_room_members_role_check;
alter table public.voice_room_members add constraint voice_room_members_role_check check (role in ('owner','admin','speaker','listener'));
drop index if exists public.voice_rooms_single_live_room;
drop function if exists public.get_or_create_voice_lobby();

create table if not exists public.voice_room_bans (
  room_id uuid not null references public.voice_rooms(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  banned_by uuid not null references auth.users(id) on delete cascade,
  reason text,
  created_at timestamptz not null default now(),
  primary key(room_id,user_id)
);
create table if not exists public.voice_room_invites (
  room_id uuid not null references public.voice_rooms(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  invited_by uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(room_id,user_id)
);
create table if not exists public.voice_room_seat_requests (
  id uuid primary key default gen_random_uuid(),
  room_id uuid not null references public.voice_rooms(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  requested_seat_index int check(requested_seat_index is null or requested_seat_index between 1 and 6),
  status text not null default 'pending' check(status in('pending','approved','denied','cancelled')),
  created_at timestamptz not null default now(),
  resolved_by uuid references auth.users(id) on delete set null,
  resolved_at timestamptz
);
create unique index if not exists voice_room_seat_requests_pending_unique on public.voice_room_seat_requests(room_id,user_id) where status='pending';
create index if not exists voice_room_requests_room_status_idx on public.voice_room_seat_requests(room_id,status,created_at);
create index if not exists voice_room_bans_user_idx on public.voice_room_bans(user_id);
create index if not exists voice_room_invites_user_idx on public.voice_room_invites(user_id);
create index if not exists voice_rooms_live_idx on public.voice_rooms(status,created_at desc) where status='live';

alter table public.voice_room_bans enable row level security;
alter table public.voice_room_invites enable row level security;
alter table public.voice_room_seat_requests enable row level security;
alter table public.voice_room_seats replica identity full;
alter table public.voice_room_members replica identity full;
alter table public.voice_room_bans replica identity full;
alter table public.voice_room_invites replica identity full;
alter table public.voice_room_seat_requests replica identity full;

create or replace function public.voice_room_is_member(p_room_id uuid) returns boolean language sql stable security definer set search_path='' as $$
select exists(select 1 from public.voice_room_members m where m.room_id=p_room_id and m.user_id=(select auth.uid()) and m.left_at is null);
$$;
create or replace function public.voice_room_is_admin(p_room_id uuid) returns boolean language sql stable security definer set search_path='' as $$
select exists(select 1 from public.voice_rooms r where r.id=p_room_id and r.owner_id=(select auth.uid())) or exists(select 1 from public.voice_room_members m where m.room_id=p_room_id and m.user_id=(select auth.uid()) and m.left_at is null and m.role='admin');
$$;
create or replace function public.voice_room_is_banned(p_room_id uuid,p_user_id uuid default null) returns boolean language sql stable security definer set search_path='' as $$
select exists(select 1 from public.voice_room_bans b where b.room_id=p_room_id and b.user_id=coalesce(p_user_id,(select auth.uid())));
$$;
create or replace function public.voice_room_can_access(p_room_id uuid) returns boolean language sql stable security definer set search_path='' as $$
select exists(select 1 from public.voice_rooms r where r.id=p_room_id and r.status='live' and not public.voice_room_is_banned(r.id,(select auth.uid())) and (r.visibility='public' or r.owner_id=(select auth.uid()) or exists(select 1 from public.voice_room_members m where m.room_id=r.id and m.user_id=(select auth.uid()) and m.left_at is null and m.role in('owner','admin')) or exists(select 1 from public.voice_room_invites i where i.room_id=r.id and i.user_id=(select auth.uid()))));
$$;

create or replace function public.voice_room_enforce_owner_limit() returns trigger language plpgsql security definer set search_path='' as $$
begin
  if new.status='live' and (select count(*) from public.voice_rooms r where r.owner_id=new.owner_id and r.status='live' and r.id<>new.id)>=3 then raise exception 'ROOM_OWNER_LIMIT: máximo 3 salas activas por usuario'; end if;
  return new;
end;
$$;
drop trigger if exists voice_room_owner_limit on public.voice_rooms;
create trigger voice_room_owner_limit before insert or update of owner_id,status on public.voice_rooms for each row execute function public.voice_room_enforce_owner_limit();

create or replace function public.create_voice_room(p_name text,p_description text default '',p_cover_url text default null,p_category text default 'general',p_visibility text default 'public') returns setof public.voice_rooms language plpgsql security definer set search_path='' as $$
declare v public.voice_rooms%rowtype;
begin
  if (select auth.uid()) is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if length(trim(coalesce(p_name,''))) not between 2 and 80 then raise exception 'INVALID_ROOM_NAME'; end if;
  if length(coalesce(p_description,''))>280 then raise exception 'INVALID_ROOM_DESCRIPTION'; end if;
  if p_visibility not in('public','private') then raise exception 'INVALID_ROOM_VISIBILITY'; end if;
  if p_category not in('general','chat','meeting','work','dating','friends','music','gaming') then raise exception 'INVALID_ROOM_CATEGORY'; end if;
  if (select count(*) from public.voice_rooms r where r.owner_id=(select auth.uid()) and r.status='live')>=3 then raise exception 'ROOM_OWNER_LIMIT: máximo 3 salas activas por usuario'; end if;
  insert into public.voice_rooms(name,owner_id,status,max_seats,description,cover_url,category,visibility,is_locked) values(trim(p_name),(select auth.uid()),'live',7,trim(coalesce(p_description,'')),nullif(trim(coalesce(p_cover_url,'')),''),p_category,p_visibility,false) returning * into v;
  insert into public.voice_room_members(room_id,user_id,role) values(v.id,(select auth.uid()),'owner');
  insert into public.voice_room_seats(room_id,seat_index,user_id,is_muted) values(v.id,0,(select auth.uid()),false);
  return next v;
end;
$$;

create or replace function public.join_voice_room(p_room_id uuid) returns void language plpgsql security definer set search_path='' as $$
declare r public.voice_rooms%rowtype;
begin
  select * into r from public.voice_rooms where id=p_room_id and status='live';
  if not found then raise exception 'ROOM_NOT_LIVE'; end if;
  if public.voice_room_is_banned(p_room_id,(select auth.uid())) then raise exception 'ROOM_BANNED'; end if;
  if r.is_locked and r.owner_id<>(select auth.uid()) and not public.voice_room_is_admin(p_room_id) then raise exception 'ROOM_LOCKED'; end if;
  if r.visibility='private' and r.owner_id<>(select auth.uid()) and not public.voice_room_is_admin(p_room_id) and not exists(select 1 from public.voice_room_invites i where i.room_id=p_room_id and i.user_id=(select auth.uid())) then raise exception 'ROOM_PRIVATE'; end if;
  if not exists(select 1 from public.voice_room_members m where m.room_id=p_room_id and m.user_id=(select auth.uid()) and m.left_at is null) then insert into public.voice_room_members(room_id,user_id,role) values(p_room_id,(select auth.uid()),'listener'); end if;
end;
$$;

create or replace function public.leave_voice_room(p_room_id uuid) returns void language plpgsql security definer set search_path='' as $$
declare owner_id uuid;
begin
  select r.owner_id into owner_id from public.voice_rooms r where r.id=p_room_id;
  if owner_id=(select auth.uid()) then
    update public.voice_rooms set status='closed',updated_at=now() where id=p_room_id and status='live';
    delete from public.voice_room_seats where room_id=p_room_id;
    update public.voice_room_members set left_at=now() where room_id=p_room_id and left_at is null;
  else
    delete from public.voice_room_seats where room_id=p_room_id and user_id=(select auth.uid());
    update public.voice_room_members set left_at=now(),role='listener' where room_id=p_room_id and user_id=(select auth.uid()) and left_at is null;
    update public.voice_room_seat_requests set status='cancelled',resolved_at=now() where room_id=p_room_id and user_id=(select auth.uid()) and status='pending';
  end if;
end;
$$;

create or replace function public.move_voice_room_seat(p_room_id uuid,p_target_seat int) returns setof public.voice_room_seats language plpgsql security definer set search_path='' as $$
declare v public.voice_room_seats%rowtype;
begin
  if p_target_seat not between 1 and 6 then raise exception 'INVALID_GUEST_SEAT'; end if;
  if not public.voice_room_is_member(p_room_id) then raise exception 'NOT_MEMBER'; end if;
  if public.voice_room_is_banned(p_room_id) then raise exception 'ROOM_BANNED'; end if;
  if exists(select 1 from public.voice_room_seats where room_id=p_room_id and seat_index=p_target_seat) then delete from public.voice_room_seats where room_id=p_room_id and user_id=(select auth.uid()); return; end if;
  delete from public.voice_room_seats where room_id=p_room_id and user_id=(select auth.uid());
  insert into public.voice_room_seats(room_id,seat_index,user_id,is_muted) values(p_room_id,p_target_seat,(select auth.uid()),false) returning * into v;
  return next v;
end;
$$;
create or replace function public.leave_voice_room_seat(p_room_id uuid) returns void language plpgsql security definer set search_path='' as $$ begin delete from public.voice_room_seats where room_id=p_room_id and user_id=(select auth.uid()) and seat_index between 1 and 6; end; $$;

create or replace function public.request_voice_room_seat(p_room_id uuid,p_requested_seat int default null) returns public.voice_room_seat_requests language plpgsql security definer set search_path='' as $$
declare v public.voice_room_seat_requests%rowtype;
begin
  if not public.voice_room_is_member(p_room_id) then raise exception 'NOT_MEMBER'; end if;
  if public.voice_room_is_banned(p_room_id) then raise exception 'ROOM_BANNED'; end if;
  if p_requested_seat is not null and p_requested_seat not between 1 and 6 then raise exception 'INVALID_GUEST_SEAT'; end if;
  if exists(select 1 from public.voice_room_seats where room_id=p_room_id and user_id=(select auth.uid())) then raise exception 'ALREADY_SEATED'; end if;
  if p_requested_seat is not null and exists(select 1 from public.voice_room_seats where room_id=p_room_id and seat_index=p_requested_seat) then raise exception 'SEAT_OCCUPIED'; end if;
  insert into public.voice_room_seat_requests(room_id,user_id,requested_seat_index) values(p_room_id,(select auth.uid()),p_requested_seat) on conflict(room_id,user_id) where status='pending' do update set requested_seat_index=excluded.requested_seat_index,created_at=now() returning * into v;
  return v;
end;
$$;

create or replace function public.resolve_voice_room_seat_request(p_request_id uuid,p_approve boolean,p_seat_index int default null) returns setof public.voice_room_seats language plpgsql security definer set search_path='' as $$
declare q public.voice_room_seat_requests%rowtype; seat int; v public.voice_room_seats%rowtype;
begin
  select * into q from public.voice_room_seat_requests where id=p_request_id and status='pending' for update;
  if not found then raise exception 'REQUEST_NOT_PENDING'; end if;
  if not public.voice_room_is_admin(q.room_id) then raise exception 'NOT_ROOM_ADMIN'; end if;
  if not p_approve then update public.voice_room_seat_requests set status='denied',resolved_by=(select auth.uid()),resolved_at=now() where id=p_request_id; return; end if;
  if public.voice_room_is_banned(q.room_id,q.user_id) then raise exception 'TARGET_BANNED'; end if;
  if p_seat_index is not null and p_seat_index not between 1 and 6 then raise exception 'INVALID_GUEST_SEAT'; end if;
  seat=coalesce(p_seat_index,q.requested_seat_index);
  if seat is null then select s into seat from generate_series(1,6) s where not exists(select 1 from public.voice_room_seats x where x.room_id=q.room_id and x.seat_index=s) limit 1; end if;
  if seat is null or exists(select 1 from public.voice_room_seats x where x.room_id=q.room_id and x.seat_index=seat) then raise exception 'NO_FREE_SEAT'; end if;
  delete from public.voice_room_seats where room_id=q.room_id and user_id=q.user_id;
  insert into public.voice_room_seats(room_id,seat_index,user_id,is_muted) values(q.room_id,seat,q.user_id,false) returning * into v;
  update public.voice_room_seat_requests set status='approved',resolved_by=(select auth.uid()),resolved_at=now() where id=p_request_id;
  return next v;
end;
$$;

create or replace function public.set_voice_room_admin(p_room_id uuid,p_user_id uuid,p_make_admin boolean) returns void language plpgsql security definer set search_path='' as $$ begin if not exists(select 1 from public.voice_rooms r where r.id=p_room_id and r.owner_id=(select auth.uid())) then raise exception 'OWNER_ONLY'; end if; if p_user_id=(select auth.uid()) then raise exception 'OWNER_ROLE_IMMUTABLE'; end if; update public.voice_room_members set role=case when p_make_admin then 'admin' else 'listener' end where room_id=p_room_id and user_id=p_user_id and left_at is null and role<>'owner'; if not found then raise exception 'TARGET_NOT_ACTIVE'; end if; end; $$;

create or replace function public.moderate_voice_room_mute(p_room_id uuid,p_target_user uuid,p_muted boolean) returns void language plpgsql security definer set search_path='' as $$ declare actor text; target text; begin select case when r.owner_id=(select auth.uid()) then 'owner' else m.role end into actor from public.voice_rooms r left join public.voice_room_members m on m.room_id=r.id and m.user_id=(select auth.uid()) and m.left_at is null where r.id=p_room_id; select case when r.owner_id=p_target_user then 'owner' else m.role end into target from public.voice_rooms r left join public.voice_room_members m on m.room_id=r.id and m.user_id=p_target_user and m.left_at is null where r.id=p_room_id; if actor not in('owner','admin') then raise exception 'NOT_ROOM_ADMIN'; end if; if target='owner' then raise exception 'OWNER_PROTECTED'; end if; if actor='admin' and target='admin' then raise exception 'ADMIN_PROTECTED'; end if; update public.voice_room_seats set is_muted=p_muted where room_id=p_room_id and user_id=p_target_user; end; $$;
create or replace function public.moderate_voice_room_kick(p_room_id uuid,p_target_user uuid) returns void language plpgsql security definer set search_path='' as $$ declare actor text; target text; begin select case when r.owner_id=(select auth.uid()) then 'owner' else m.role end into actor from public.voice_rooms r left join public.voice_room_members m on m.room_id=r.id and m.user_id=(select auth.uid()) and m.left_at is null where r.id=p_room_id; select case when r.owner_id=p_target_user then 'owner' else m.role end into target from public.voice_rooms r left join public.voice_room_members m on m.room_id=r.id and m.user_id=p_target_user and m.left_at is null where r.id=p_room_id; if actor not in('owner','admin') then raise exception 'NOT_ROOM_ADMIN'; end if; if target='owner' then raise exception 'OWNER_PROTECTED'; end if; if actor='admin' and target='admin' then raise exception 'ADMIN_PROTECTED'; end if; delete from public.voice_room_seats where room_id=p_room_id and user_id=p_target_user; update public.voice_room_members set left_at=now(),role='listener' where room_id=p_room_id and user_id=p_target_user and left_at is null; end; $$;
create or replace function public.moderate_voice_room_ban(p_room_id uuid,p_target_user uuid,p_reason text default null) returns void language plpgsql security definer set search_path='' as $$ declare actor text; target text; begin select case when r.owner_id=(select auth.uid()) then 'owner' else m.role end into actor from public.voice_rooms r left join public.voice_room_members m on m.room_id=r.id and m.user_id=(select auth.uid()) and m.left_at is null where r.id=p_room_id; select case when r.owner_id=p_target_user then 'owner' else m.role end into target from public.voice_rooms r left join public.voice_room_members m on m.room_id=r.id and m.user_id=p_target_user and m.left_at is null where r.id=p_room_id; if actor not in('owner','admin') then raise exception 'NOT_ROOM_ADMIN'; end if; if target='owner' then raise exception 'OWNER_PROTECTED'; end if; if actor='admin' and target='admin' then raise exception 'ADMIN_PROTECTED'; end if; insert into public.voice_room_bans(room_id,user_id,banned_by,reason) values(p_room_id,p_target_user,(select auth.uid()),nullif(trim(coalesce(p_reason,'')),'')) on conflict(room_id,user_id) do update set banned_by=excluded.banned_by,reason=excluded.reason,created_at=now(); delete from public.voice_room_seats where room_id=p_room_id and user_id=p_target_user; update public.voice_room_members set left_at=now(),role='listener' where room_id=p_room_id and user_id=p_target_user and left_at is null; end; $$;
create or replace function public.invite_voice_room_user(p_room_id uuid,p_user_id uuid) returns void language plpgsql security definer set search_path='' as $$ begin if not public.voice_room_is_admin(p_room_id) then raise exception 'NOT_ROOM_ADMIN'; end if; if public.voice_room_is_banned(p_room_id,p_user_id) then raise exception 'TARGET_BANNED'; end if; insert into public.voice_room_invites(room_id,user_id,invited_by) values(p_room_id,p_user_id,(select auth.uid())) on conflict(room_id,user_id) do update set invited_by=excluded.invited_by,created_at=now(); end; $$;

-- Discovery is multi-room. Mutations go through RPCs so clients cannot bypass moderation/seat rules.
drop policy if exists voice_rooms_select on public.voice_rooms; create policy voice_rooms_select on public.voice_rooms for select to authenticated using(status='live' or owner_id=(select auth.uid()));
drop policy if exists voice_rooms_insert on public.voice_rooms; create policy voice_rooms_insert on public.voice_rooms for insert to authenticated with check(false);
drop policy if exists voice_rooms_update on public.voice_rooms; create policy voice_rooms_update on public.voice_rooms for update to authenticated using(false) with check(false);
drop policy if exists voice_room_members_select on public.voice_room_members; create policy voice_room_members_select on public.voice_room_members for select to authenticated using(public.voice_room_is_member(room_id) or user_id=(select auth.uid()));
drop policy if exists voice_room_members_insert on public.voice_room_members; create policy voice_room_members_insert on public.voice_room_members for insert to authenticated with check(false);
drop policy if exists voice_room_members_update on public.voice_room_members; create policy voice_room_members_update on public.voice_room_members for update to authenticated using(false) with check(false);
drop policy if exists voice_room_seats_select on public.voice_room_seats; create policy voice_room_seats_select on public.voice_room_seats for select to authenticated using(public.voice_room_is_member(room_id));
drop policy if exists voice_room_seats_insert on public.voice_room_seats; create policy voice_room_seats_insert on public.voice_room_seats for insert to authenticated with check(false);
drop policy if exists voice_room_seats_update on public.voice_room_seats; create policy voice_room_seats_update on public.voice_room_seats for update to authenticated using(false) with check(false);
drop policy if exists voice_room_seats_delete on public.voice_room_seats; create policy voice_room_seats_delete on public.voice_room_seats for delete to authenticated using(false);
drop policy if exists voice_room_bans_select on public.voice_room_bans; create policy voice_room_bans_select on public.voice_room_bans for select to authenticated using(public.voice_room_is_admin(room_id));
drop policy if exists voice_room_bans_insert on public.voice_room_bans; create policy voice_room_bans_insert on public.voice_room_bans for insert to authenticated with check(false);
drop policy if exists voice_room_bans_update on public.voice_room_bans; create policy voice_room_bans_update on public.voice_room_bans for update to authenticated using(false) with check(false);
drop policy if exists voice_room_bans_delete on public.voice_room_bans; create policy voice_room_bans_delete on public.voice_room_bans for delete to authenticated using(false);
drop policy if exists voice_room_invites_select on public.voice_room_invites; create policy voice_room_invites_select on public.voice_room_invites for select to authenticated using(user_id=(select auth.uid()) or public.voice_room_is_admin(room_id));
drop policy if exists voice_room_invites_insert on public.voice_room_invites; create policy voice_room_invites_insert on public.voice_room_invites for insert to authenticated with check(false);
drop policy if exists voice_room_invites_update on public.voice_room_invites; create policy voice_room_invites_update on public.voice_room_invites for update to authenticated using(false) with check(false);
drop policy if exists voice_room_invites_delete on public.voice_room_invites; create policy voice_room_invites_delete on public.voice_room_invites for delete to authenticated using(false);
drop policy if exists voice_room_seat_requests_select on public.voice_room_seat_requests; create policy voice_room_seat_requests_select on public.voice_room_seat_requests for select to authenticated using(user_id=(select auth.uid()) or public.voice_room_is_admin(room_id));
drop policy if exists voice_room_seat_requests_insert on public.voice_room_seat_requests; create policy voice_room_seat_requests_insert on public.voice_room_seat_requests for insert to authenticated with check(false);
drop policy if exists voice_room_seat_requests_update on public.voice_room_seat_requests; create policy voice_room_seat_requests_update on public.voice_room_seat_requests for update to authenticated using(false) with check(false);
drop policy if exists voice_room_messages_select on public.voice_room_messages; create policy voice_room_messages_select on public.voice_room_messages for select to authenticated using(public.voice_room_is_member(room_id));
drop policy if exists voice_room_messages_insert on public.voice_room_messages; create policy voice_room_messages_insert on public.voice_room_messages for insert to authenticated with check(sender_id=(select auth.uid()) and public.voice_room_is_member(room_id) and not public.voice_room_is_banned(room_id));

grant select on public.voice_rooms,public.voice_room_members,public.voice_room_seats,public.voice_room_messages,public.voice_room_bans,public.voice_room_invites,public.voice_room_seat_requests to authenticated;
grant insert on public.voice_room_messages to authenticated;
revoke execute on function public.voice_room_is_member(uuid),public.voice_room_is_admin(uuid),public.voice_room_is_banned(uuid,uuid),public.voice_room_can_access(uuid),public.create_voice_room(text,text,text,text,text),public.join_voice_room(uuid),public.leave_voice_room(uuid),public.move_voice_room_seat(uuid,integer),public.leave_voice_room_seat(uuid),public.request_voice_room_seat(uuid,integer),public.resolve_voice_room_seat_request(uuid,boolean,integer),public.set_voice_room_admin(uuid,uuid,boolean),public.moderate_voice_room_mute(uuid,uuid,boolean),public.moderate_voice_room_kick(uuid,uuid),public.moderate_voice_room_ban(uuid,uuid,text),public.invite_voice_room_user(uuid,uuid) from public,anon;
grant execute on function public.voice_room_is_member(uuid),public.voice_room_is_admin(uuid),public.voice_room_is_banned(uuid,uuid),public.voice_room_can_access(uuid),public.create_voice_room(text,text,text,text,text),public.join_voice_room(uuid),public.leave_voice_room(uuid),public.move_voice_room_seat(uuid,integer),public.leave_voice_room_seat(uuid),public.request_voice_room_seat(uuid,integer),public.resolve_voice_room_seat_request(uuid,boolean,integer),public.set_voice_room_admin(uuid,uuid,boolean),public.moderate_voice_room_mute(uuid,uuid,boolean),public.moderate_voice_room_kick(uuid,uuid),public.moderate_voice_room_ban(uuid,uuid,text),public.invite_voice_room_user(uuid,uuid) to authenticated;

do $$ begin
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='voice_room_bans') then alter publication supabase_realtime add table public.voice_room_bans; end if;
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='voice_room_invites') then alter publication supabase_realtime add table public.voice_room_invites; end if;
  if not exists(select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='voice_room_seat_requests') then alter publication supabase_realtime add table public.voice_room_seat_requests; end if;
end $$;

-- Public cover bucket; files are scoped to the authenticated owner's folder.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values('voice-room-covers','voice-room-covers',true,5242880,array['image/jpeg','image/png','image/webp']) on conflict(id) do update set public=true,file_size_limit=5242880,allowed_mime_types=excluded.allowed_mime_types;
drop policy if exists voice_room_covers_insert on storage.objects; create policy voice_room_covers_insert on storage.objects for insert to authenticated with check(bucket_id='voice-room-covers' and (storage.foldername(name))[1]=(select auth.uid())::text);
drop policy if exists voice_room_covers_update on storage.objects; create policy voice_room_covers_update on storage.objects for update to authenticated using(bucket_id='voice-room-covers' and owner_id=(select auth.uid())::text) with check(bucket_id='voice-room-covers' and owner_id=(select auth.uid())::text);
drop policy if exists voice_room_covers_delete on storage.objects; create policy voice_room_covers_delete on storage.objects for delete to authenticated using(bucket_id='voice-room-covers' and owner_id=(select auth.uid())::text);
