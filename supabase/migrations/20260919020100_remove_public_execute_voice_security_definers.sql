-- CREATE OR REPLACE FUNCTION restores the default PUBLIC EXECUTE grant.
-- Explicitly remove it from authenticated-only voice-room SECURITY DEFINER RPCs.

revoke execute on function public.close_voice_room(uuid) from public;
revoke execute on function public.create_voice_room(text,text,text,text,text) from public;
revoke execute on function public.get_voice_room_decor(uuid) from public;
revoke execute on function public.leave_voice_room(uuid) from public;
revoke execute on function public.move_voice_room_seat(uuid,integer) from public;
revoke execute on function public.record_voice_room_entrance(uuid,text) from public;
revoke execute on function public.request_voice_room_seat(uuid,integer) from public;
revoke execute on function public.set_voice_room_entrance(uuid,text) from public;
revoke execute on function public.set_voice_room_pendant(uuid,text) from public;
