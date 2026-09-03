-- Voice rooms: expand to 9 seats (1 host + 8 guests), auto-clear chat when empty.
-- Idempotent. Applied live 2026-08-27.

-- 1. Relax structural checks: seats 0..8, max_seats in (7,9) for back-compat.
alter table public.voice_rooms drop constraint if exists voice_rooms_max_seats_check;
alter table public.voice_rooms add  constraint voice_rooms_max_seats_check check (max_seats in (7,9));

alter table public.voice_room_seats drop constraint if exists voice_room_seats_seat_index_check;
alter table public.voice_room_seats add  constraint voice_room_seats_seat_index_check check (seat_index between 0 and 8);

-- 2. New rooms get 9 seats.
create or replace function public.create_voice_room(
  p_name text, p_description text default '', p_category text default 'general',
  p_visibility text default 'public', p_cover_url text default null
) returns table (
  id uuid, name text, owner_id uuid, status text, max_seats int,
  description text, cover_url text, category text, visibility text, is_locked boolean, created_at timestamptz
)
language plpgsql security definer set search_path = public as $$
declare v_room public.voice_rooms%rowtype;
begin
  if (select auth.uid()) is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if length(trim(coalesce(p_name,'')))<2 or length(trim(coalesce(p_name,'')))>80 then raise exception 'INVALID_ROOM_NAME'; end if;
  if length(coalesce(p_description,''))>280 then raise exception 'INVALID_ROOM_DESCRIPTION'; end if;
  if p_visibility not in('public','private') then raise exception 'INVALID_ROOM_VISIBILITY'; end if;
  if p_category not in('general','chat','meeting','work','dating','friends','music','gaming') then raise exception 'INVALID_ROOM_CATEGORY'; end if;
  if (select count(*) from public.voice_rooms r where r.owner_id=(select auth.uid()) and r.status='live')>=3 then
    raise exception 'ROOM_OWNER_LIMIT: máximo 3 salas activas por usuario';
  end if;
  insert into public.voice_rooms(name,owner_id,status,max_seats,description,cover_url,category,visibility,is_locked)
  values(trim(p_name),(select auth.uid()),'live',9,trim(coalesce(p_description,'')),nullif(trim(coalesce(p_cover_url,'')),''),p_category,p_visibility,false)
  returning * into v_room;
  insert into public.voice_room_members(room_id,user_id,role) values(v_room.id,(select auth.uid()),'owner');
  insert into public.voice_room_seats(room_id,seat_index,user_id,is_muted) values(v_room.id,0,(select auth.uid()),false);
  return query select v_room.id, v_room.name, v_room.owner_id, v_room.status, v_room.max_seats,
    v_room.description, v_room.cover_url, v_room.category, v_room.visibility, v_room.is_locked, v_room.created_at;
end;
$$;

-- 3. move_voice_room_seat: accept seats 0..8.
create or replace function public.move_voice_room_seat(p_room_id uuid, p_target_seat int)
returns table (
  id uuid, room_id uuid, seat_index int, user_id uuid, is_muted boolean, joined_at timestamptz
)
language plpgsql security definer set search_path = public as $$
declare
  v_actor uuid := (select auth.uid());
  v_owner uuid;
  v_is_admin boolean;
  v_target uuid;
begin
  if v_actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_target_seat not between 0 and 8 then raise exception 'INVALID_SEAT'; end if;

  select r.owner_id into v_owner
    from public.voice_rooms r
   where r.id=p_room_id and r.status='live';
  if v_owner is null then raise exception 'ROOM_NOT_LIVE'; end if;

  if public.voice_room_is_banned(p_room_id,v_actor) then raise exception 'ROOM_BANNED'; end if;
  if not exists(select 1 from public.voice_room_members m where m.room_id=p_room_id and m.user_id=v_actor and m.left_at is null) then
    raise exception 'NOT_MEMBER';
  end if;

  v_is_admin := (v_actor=v_owner) or exists(
    select 1 from public.voice_room_members m
    where m.room_id=p_room_id and m.user_id=v_actor and m.left_at is null and m.role='admin'
  );

  if p_target_seat=0 and not v_is_admin then
    raise exception 'HOST_SEAT_ADMIN_ONLY';
  end if;

  select s.user_id into v_target
    from public.voice_room_seats s
   where s.room_id=p_room_id and s.seat_index=p_target_seat;

  if v_target is not null and v_target<>v_actor then
    if v_target=v_owner and v_actor<>v_owner then
      raise exception 'OWNER_SEAT_PROTECTED';
    end if;
    if v_is_admin then
      delete from public.voice_room_seats where room_id=p_room_id and user_id=v_target;
    else
      raise exception 'SEAT_OCCUPIED';
    end if;
  end if;

  delete from public.voice_room_seats where room_id=p_room_id and user_id=v_actor;
  insert into public.voice_room_seats(room_id,seat_index,user_id,is_muted)
  values(p_room_id,p_target_seat,v_actor,false)
  returning voice_room_seats.id, voice_room_seats.room_id, voice_room_seats.seat_index,
            voice_room_seats.user_id, voice_room_seats.is_muted, voice_room_seats.joined_at into v_target;
  return query select voice_room_seats.id, voice_room_seats.room_id, voice_room_seats.seat_index,
    voice_room_seats.user_id, voice_room_seats.is_muted, voice_room_seats.joined_at
    from public.voice_room_seats where voice_room_seats.room_id=p_room_id and voice_room_seats.user_id=v_actor;
end;
$$;

-- 4. request_voice_room_seat: guest seats are 1..8.
create or replace function public.request_voice_room_seat(p_room_id uuid, p_requested_seat int default null)
returns table (
  id uuid, room_id uuid, user_id uuid, requested_seat_index int, status text, created_at timestamptz
)
language plpgsql security definer set search_path = public as $$
declare v_req public.voice_room_seat_requests%rowtype;
begin
  if not public.voice_room_is_member(p_room_id) then raise exception 'NOT_MEMBER'; end if;
  if public.voice_room_is_banned(p_room_id) then raise exception 'ROOM_BANNED'; end if;
  if p_requested_seat is not null and (p_requested_seat<1 or p_requested_seat>8) then raise exception 'INVALID_GUEST_SEAT'; end if;
  if exists(select 1 from public.voice_room_seats where room_id=p_room_id and user_id=(select auth.uid())) then raise exception 'ALREADY_SEATED'; end if;
  if p_requested_seat is not null and exists(select 1 from public.voice_room_seats where room_id=p_room_id and seat_index=p_requested_seat) then raise exception 'SEAT_OCCUPIED'; end if;
  insert into public.voice_room_seat_requests(room_id,user_id,requested_seat_index)
  values(p_room_id,(select auth.uid()),p_requested_seat)
  returning * into v_req;
  return query select v_req.id, v_req.room_id, v_req.user_id, v_req.requested_seat_index, v_req.status, v_req.created_at;
end;
$$;

-- 5. leave_voice_room: when the last active member leaves, clear chat + close.
create or replace function public.leave_voice_room(p_room_id uuid)
returns void
language plpgsql security definer set search_path = public as $$
declare v_remaining int;
begin
  if not exists (
    select 1 from public.voice_room_members m
    where m.room_id=p_room_id and m.user_id=(select auth.uid()) and m.left_at is null
  ) and not exists (
    select 1 from public.voice_rooms r
    where r.id=p_room_id and r.owner_id=(select auth.uid())
  ) then
    raise exception 'NOT_ROOM_MEMBER';
  end if;

  delete from public.voice_room_seats
   where room_id=p_room_id and user_id=(select auth.uid());

  update public.voice_room_members
     set left_at=now(), role='listener'
   where room_id=p_room_id
     and user_id=(select auth.uid())
     and left_at is null;

  update public.voice_room_seat_requests
     set status='cancelled', resolved_at=now()
   where room_id=p_room_id
     and user_id=(select auth.uid())
     and status='pending';

  select count(*) into v_remaining
    from public.voice_room_members
   where room_id=p_room_id and left_at is null;

  if v_remaining = 0 then
    -- Room is empty: wipe the chat so re-entering starts fresh. The room
    -- stays 'live' so it remains discoverable in the browser and can be
    -- re-joined; closing only happens on an explicit close_voice_room.
    delete from public.voice_room_messages where room_id=p_room_id;
  end if;
end;
$$;

-- 6. close_voice_room: also wipe chat on explicit close.
create or replace function public.close_voice_room(p_room_id uuid)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if not exists(select 1 from public.voice_rooms r where r.id=p_room_id and r.owner_id=(select auth.uid())) then
    raise exception 'OWNER_ONLY';
  end if;
  delete from public.voice_room_messages where room_id=p_room_id;
  update public.voice_rooms
     set status='closed', updated_at=now()
   where id=p_room_id and status='live';
end;
$$;

-- Backfill existing live rooms to 9 seats so the new layout has room.
update public.voice_rooms set max_seats=9 where status='live' and max_seats=7;
