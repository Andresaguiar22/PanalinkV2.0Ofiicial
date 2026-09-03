-- Voice Rooms — function execution hardening.
-- SECURITY DEFINER functions in public must not retain PUBLIC/anon EXECUTE.
-- Client RPCs are exposed only to authenticated users; trigger helpers are
-- intentionally not granted direct execution because PostgreSQL invokes them
-- through their triggers.

revoke all on function public.close_voice_room(uuid) from public, anon;
revoke all on function public.create_voice_room(text,text,text,text,text) from public, anon;
revoke all on function public.invite_voice_room_user(uuid,uuid) from public, anon;
revoke all on function public.is_voice_room_member(uuid) from public, anon;
revoke all on function public.join_voice_room(uuid) from public, anon;
revoke all on function public.leave_voice_room(uuid) from public, anon;
revoke all on function public.leave_voice_room_seat(uuid) from public, anon;
revoke all on function public.moderate_voice_room_ban(uuid,uuid,text) from public, anon;
revoke all on function public.moderate_voice_room_kick(uuid,uuid) from public, anon;
revoke all on function public.moderate_voice_room_mute(uuid,uuid,boolean) from public, anon;
revoke all on function public.move_voice_room_seat(uuid,integer) from public, anon;
revoke all on function public.request_voice_room_seat(uuid,integer) from public, anon;
revoke all on function public.resolve_voice_room_seat_request(uuid,boolean,integer) from public, anon;
revoke all on function public.set_voice_room_admin(uuid,uuid,boolean) from public, anon;
revoke all on function public.voice_room_can_access(uuid) from public, anon;
revoke all on function public.voice_room_is_admin(uuid) from public, anon;
revoke all on function public.voice_room_is_banned(uuid,uuid) from public, anon;
revoke all on function public.voice_room_is_member(uuid) from public, anon;

revoke all on function public.voice_room_enforce_owner_limit() from public, anon;
revoke all on function public.voice_room_member_integrity() from public, anon;
revoke all on function public.voice_room_member_leave_cleanup() from public, anon;
revoke all on function public.voice_room_seat_integrity() from public, anon;
revoke all on function public.voice_room_seat_role_sync() from public, anon;

grant execute on function public.close_voice_room(uuid) to authenticated;
grant execute on function public.create_voice_room(text,text,text,text,text) to authenticated;
grant execute on function public.invite_voice_room_user(uuid,uuid) to authenticated;
grant execute on function public.is_voice_room_member(uuid) to authenticated;
grant execute on function public.join_voice_room(uuid) to authenticated;
grant execute on function public.leave_voice_room(uuid) to authenticated;
grant execute on function public.leave_voice_room_seat(uuid) to authenticated;
grant execute on function public.moderate_voice_room_ban(uuid,uuid,text) to authenticated;
grant execute on function public.moderate_voice_room_kick(uuid,uuid) to authenticated;
grant execute on function public.moderate_voice_room_mute(uuid,uuid,boolean) to authenticated;
grant execute on function public.move_voice_room_seat(uuid,integer) to authenticated;
grant execute on function public.request_voice_room_seat(uuid,integer) to authenticated;
grant execute on function public.resolve_voice_room_seat_request(uuid,boolean,integer) to authenticated;
grant execute on function public.set_voice_room_admin(uuid,uuid,boolean) to authenticated;
grant execute on function public.voice_room_can_access(uuid) to authenticated;
grant execute on function public.voice_room_is_admin(uuid) to authenticated;
grant execute on function public.voice_room_is_banned(uuid,uuid) to authenticated;
grant execute on function public.voice_room_is_member(uuid) to authenticated;
