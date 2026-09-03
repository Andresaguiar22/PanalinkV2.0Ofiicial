-- Post-v2 hardening: the original member-integrity trigger must not overwrite admin promotions.
create or replace function public.voice_room_member_integrity() returns trigger
language plpgsql security definer set search_path='pg_catalog','public' as $$
declare v_owner_id uuid; v_status text;
begin
  select r.owner_id,r.status into v_owner_id,v_status from public.voice_rooms r where r.id=new.room_id;
  if v_owner_id is null then raise exception 'voice room does not exist'; end if;
  if tg_op='INSERT' then
    if v_status<>'live' then raise exception 'voice room is closed'; end if;
    if new.user_id<>auth.uid() then raise exception 'cannot join as another user'; end if;
    new.role:=case when new.user_id=v_owner_id then 'owner' when exists(select 1 from public.voice_room_seats s where s.room_id=new.room_id and s.user_id=new.user_id) then 'speaker' else 'listener' end;
    return new;
  end if;
  if old.left_at is null and new.left_at is null and (new.room_id<>old.room_id or new.user_id<>old.user_id) then raise exception 'active voice room membership identity is immutable'; end if;
  if old.left_at is not null and new.left_at is null then raise exception 'voice room membership cannot be reactivated'; end if;
  if new.user_id=v_owner_id then new.role='owner'; elsif new.left_at is not null then new.role='listener'; end if;
  return new;
end;
$$;

-- Composite RPCs return SETOF so a successful operation that intentionally yields no row serializes as [] instead of JSON null.
drop function if exists public.move_voice_room_seat(uuid,integer);
create function public.move_voice_room_seat(p_room_id uuid,p_target_seat int) returns setof public.voice_room_seats language plpgsql security definer set search_path='' as $$
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

drop function if exists public.resolve_voice_room_seat_request(uuid,boolean,integer);
create function public.resolve_voice_room_seat_request(p_request_id uuid,p_approve boolean,p_seat_index int default null) returns setof public.voice_room_seats language plpgsql security definer set search_path='' as $$
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

revoke execute on function public.voice_room_is_member(uuid),public.voice_room_is_admin(uuid),public.voice_room_is_banned(uuid,uuid),public.voice_room_can_access(uuid),public.create_voice_room(text,text,text,text,text),public.join_voice_room(uuid),public.leave_voice_room(uuid),public.move_voice_room_seat(uuid,integer),public.leave_voice_room_seat(uuid),public.request_voice_room_seat(uuid,integer),public.resolve_voice_room_seat_request(uuid,boolean,integer),public.set_voice_room_admin(uuid,uuid,boolean),public.moderate_voice_room_mute(uuid,uuid,boolean),public.moderate_voice_room_kick(uuid,uuid),public.moderate_voice_room_ban(uuid,uuid,text),public.invite_voice_room_user(uuid,uuid) from public,anon;
grant execute on function public.voice_room_is_member(uuid),public.voice_room_is_admin(uuid),public.voice_room_is_banned(uuid,uuid),public.voice_room_can_access(uuid),public.create_voice_room(text,text,text,text,text),public.join_voice_room(uuid),public.leave_voice_room(uuid),public.move_voice_room_seat(uuid,integer),public.leave_voice_room_seat(uuid),public.request_voice_room_seat(uuid,integer),public.resolve_voice_room_seat_request(uuid,boolean,integer),public.set_voice_room_admin(uuid,uuid,boolean),public.moderate_voice_room_mute(uuid,uuid,boolean),public.moderate_voice_room_kick(uuid,uuid),public.moderate_voice_room_ban(uuid,uuid,text),public.invite_voice_room_user(uuid,uuid) to authenticated;
