-- Fix: column reference "room_id" is ambiguous in move_voice_room_seat and request_voice_room_seat.
-- Root cause: RETURNS TABLE(...) creates PL/pgSQL output variables that shadow table columns.
-- Any unqualified reference to room_id/user_id/seat_index is ambiguous (variable vs column).
-- Also: leave_voice_room_seat allowed seats 1-6 but max_seats is now 9 (seats 0-8).

-- 1. move_voice_room_seat: qualify all unqualified room_id/user_id in DELETE statements.
CREATE OR REPLACE FUNCTION public.move_voice_room_seat(p_room_id uuid, p_target_seat integer)
 RETURNS TABLE(id uuid, room_id uuid, seat_index integer, user_id uuid, is_muted boolean, joined_at timestamp with time zone)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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
      delete from public.voice_room_seats where voice_room_seats.room_id=p_room_id and voice_room_seats.user_id=v_target;
    else
      raise exception 'SEAT_OCCUPIED';
    end if;
  end if;

  delete from public.voice_room_seats where voice_room_seats.room_id=p_room_id and voice_room_seats.user_id=v_actor;
  insert into public.voice_room_seats(room_id,seat_index,user_id,is_muted)
  values(p_room_id,p_target_seat,v_actor,false)
  returning voice_room_seats.id, voice_room_seats.room_id, voice_room_seats.seat_index,
            voice_room_seats.user_id, voice_room_seats.is_muted, voice_room_seats.joined_at into v_target;
  return query select voice_room_seats.id, voice_room_seats.room_id, voice_room_seats.seat_index,
    voice_room_seats.user_id, voice_room_seats.is_muted, voice_room_seats.joined_at
    from public.voice_room_seats where voice_room_seats.room_id=p_room_id and voice_room_seats.user_id=v_actor;
end;
$function$;

-- 2. request_voice_room_seat: qualify all unqualified room_id/user_id/seat_index in WHERE clauses.
CREATE OR REPLACE FUNCTION public.request_voice_room_seat(p_room_id uuid, p_requested_seat integer DEFAULT NULL::integer)
 RETURNS TABLE(id uuid, room_id uuid, user_id uuid, requested_seat_index integer, status text, created_at timestamp with time zone)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_req public.voice_room_seat_requests%rowtype;
begin
  if not public.voice_room_is_member(p_room_id) then raise exception 'NOT_MEMBER'; end if;
  if public.voice_room_is_banned(p_room_id) then raise exception 'ROOM_BANNED'; end if;
  if p_requested_seat is not null and (p_requested_seat<1 or p_requested_seat>8) then raise exception 'INVALID_GUEST_SEAT'; end if;
  if exists(select 1 from public.voice_room_seats where voice_room_seats.room_id=p_room_id and voice_room_seats.user_id=(select auth.uid())) then raise exception 'ALREADY_SEATED'; end if;
  if p_requested_seat is not null and exists(select 1 from public.voice_room_seats where voice_room_seats.room_id=p_room_id and voice_room_seats.seat_index=p_requested_seat) then raise exception 'SEAT_OCCUPIED'; end if;
  insert into public.voice_room_seat_requests(room_id,user_id,requested_seat_index)
  values(p_room_id,(select auth.uid()),p_requested_seat)
  returning * into v_req;
  return query select v_req.id, v_req.room_id, v_req.user_id, v_req.requested_seat_index, v_req.status, v_req.created_at;
end;
$function$;

-- 3. leave_voice_room_seat: fix seat range 1-8 (was 1-6, leaving seats 7-8 impossible) + set search_path.
CREATE OR REPLACE FUNCTION public.leave_voice_room_seat(p_room_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare v_owner uuid;
begin
  select public.voice_rooms.owner_id into v_owner from public.voice_rooms where public.voice_rooms.id=p_room_id and public.voice_rooms.status='live';
  if v_owner is null then raise exception 'ROOM_NOT_LIVE'; end if;
  if not exists(select 1 from public.voice_room_members where public.voice_room_members.room_id=p_room_id and public.voice_room_members.user_id=(select auth.uid()) and public.voice_room_members.left_at is null) then
    raise exception 'NOT_MEMBER';
  end if;
  delete from public.voice_room_seats
   where public.voice_room_seats.room_id=p_room_id
     and public.voice_room_seats.user_id=(select auth.uid())
     and (public.voice_room_seats.seat_index between 1 and 8 or (public.voice_room_seats.seat_index=0 and ((select auth.uid())=v_owner or public.voice_room_is_admin(p_room_id))));
end;
$function$;
