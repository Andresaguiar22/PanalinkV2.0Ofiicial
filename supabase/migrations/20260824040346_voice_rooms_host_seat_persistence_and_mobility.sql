-- Voice Rooms: host/admin seat mobility and persistent rooms.
-- Owner leaving the room does NOT close the room; closing is explicit.

create or replace function public.leave_voice_room(p_room_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if not exists (select 1 from public.voice_room_members m where m.room_id=p_room_id and m.user_id=(select auth.uid()) and m.left_at is null)
     and not exists (select 1 from public.voice_rooms r where r.id=p_room_id and r.owner_id=(select auth.uid())) then
    raise exception 'NOT_ROOM_MEMBER';
  end if;
  delete from public.voice_room_seats where room_id=p_room_id and user_id=(select auth.uid());
  update public.voice_room_members set left_at=now(), role='listener' where room_id=p_room_id and user_id=(select auth.uid()) and left_at is null;
  update public.voice_room_seat_requests set status='cancelled',resolved_at=now() where room_id=p_room_id and user_id=(select auth.uid()) and status='pending';
end;
$$;
revoke all on function public.leave_voice_room(uuid) from public;
grant execute on function public.leave_voice_room(uuid) to authenticated;

create or replace function public.close_voice_room(p_room_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if not exists(select 1 from public.voice_rooms r where r.id=p_room_id and r.owner_id=(select auth.uid())) then raise exception 'OWNER_ONLY'; end if;
  update public.voice_rooms set status='closed',updated_at=now() where id=p_room_id and status='live';
end;
$$;
revoke all on function public.close_voice_room(uuid) from public;
grant execute on function public.close_voice_room(uuid) to authenticated;

create or replace function public.move_voice_room_seat(p_room_id uuid,p_target_seat integer)
returns setof public.voice_room_seats language plpgsql security definer set search_path = '' as $$
declare v_actor uuid := (select auth.uid()); v_owner uuid; v_is_admin boolean; v_target uuid; v public.voice_room_seats%rowtype;
begin
  if v_actor is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_target_seat not between 0 and 6 then raise exception 'INVALID_SEAT'; end if;
  select r.owner_id into v_owner from public.voice_rooms r where r.id=p_room_id and r.status='live';
  if v_owner is null then raise exception 'ROOM_NOT_LIVE'; end if;
  if public.voice_room_is_banned(p_room_id,v_actor) then raise exception 'ROOM_BANNED'; end if;
  if not exists(select 1 from public.voice_room_members m where m.room_id=p_room_id and m.user_id=v_actor and m.left_at is null) then raise exception 'NOT_MEMBER'; end if;
  v_is_admin := (v_actor=v_owner) or exists(select 1 from public.voice_room_members m where m.room_id=p_room_id and m.user_id=v_actor and m.left_at is null and m.role='admin');
  if p_target_seat=0 and not v_is_admin then raise exception 'HOST_SEAT_ADMIN_ONLY'; end if;
  select s.user_id into v_target from public.voice_room_seats s where s.room_id=p_room_id and s.seat_index=p_target_seat;
  if v_target is not null and v_target<>v_actor then
    if v_target=v_owner and v_actor<>v_owner then raise exception 'OWNER_SEAT_PROTECTED'; end if;
    if v_is_admin then delete from public.voice_room_seats where room_id=p_room_id and user_id=v_target;
    else delete from public.voice_room_seats where room_id=p_room_id and user_id=v_actor; return; end if;
  end if;
  delete from public.voice_room_seats where room_id=p_room_id and user_id=v_actor;
  insert into public.voice_room_seats(room_id,seat_index,user_id,is_muted) values(p_room_id,p_target_seat,v_actor,false) returning * into v;
  return next v;
end;
$$;
revoke all on function public.move_voice_room_seat(uuid,integer) from public;
grant execute on function public.move_voice_room_seat(uuid,integer) to authenticated;

create or replace function public.leave_voice_room_seat(p_room_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare v_owner uuid;
begin
  select owner_id into v_owner from public.voice_rooms where id=p_room_id and status='live';
  if v_owner is null then raise exception 'ROOM_NOT_LIVE'; end if;
  if not exists(select 1 from public.voice_room_members where room_id=p_room_id and user_id=(select auth.uid()) and left_at is null) then raise exception 'NOT_MEMBER'; end if;
  delete from public.voice_room_seats where room_id=p_room_id and user_id=(select auth.uid()) and (seat_index between 1 and 6 or (seat_index=0 and ((select auth.uid())=v_owner or public.voice_room_is_admin(p_room_id))));
end;
$$;
revoke all on function public.leave_voice_room_seat(uuid) from public;
grant execute on function public.leave_voice_room_seat(uuid) to authenticated;

create or replace function public.voice_room_seat_integrity()
returns trigger language plpgsql security definer set search_path = 'pg_catalog','public' as $$
declare v_owner uuid; v_role text;
begin
  select r.owner_id into v_owner from public.voice_rooms r where r.id=new.room_id and r.status='live';
  if v_owner is null then raise exception 'voice room is not live'; end if;
  if new.seat_index=0 then
    if new.user_id=v_owner then return new; end if;
    select m.role into v_role from public.voice_room_members m where m.room_id=new.room_id and m.user_id=new.user_id and m.left_at is null;
    if v_role<>'admin' then raise exception 'HOST_SEAT_ADMIN_ONLY'; end if;
  end if;
  return new;
end;
$$;

drop trigger if exists voice_room_seat_integrity on public.voice_room_seats;
create trigger voice_room_seat_integrity before insert or update of room_id,seat_index,user_id on public.voice_room_seats for each row execute function public.voice_room_seat_integrity();
