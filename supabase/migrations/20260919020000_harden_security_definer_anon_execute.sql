-- Harden SECURITY DEFINER RPC exposure.
-- User-facing voice-room RPCs remain available to authenticated users only.
-- Trigger/maintenance functions are not callable through the Data API.

revoke execute on function public.close_voice_room(uuid) from anon;
revoke execute on function public.create_voice_room(text,text,text,text,text) from anon;
revoke execute on function public.get_voice_room_decor(uuid) from anon;
revoke execute on function public.leave_voice_room(uuid) from anon;
revoke execute on function public.live_heartbeat(uuid) from anon;
revoke execute on function public.move_voice_room_seat(uuid,integer) from anon;
revoke execute on function public.record_voice_room_entrance(uuid,text) from anon;
revoke execute on function public.request_voice_room_seat(uuid,integer) from anon;
revoke execute on function public.set_voice_room_entrance(uuid,text) from anon;
revoke execute on function public.set_voice_room_pendant(uuid,text) from anon;

revoke execute on function public.fcm_notify_on_friend_request() from public, anon, authenticated;
revoke execute on function public.fcm_notify_on_friend_request_accepted() from public, anon, authenticated;
revoke execute on function public.notify_sender_on_contact_request_decline() from public, anon, authenticated;
revoke execute on function social.cleanup_expired_story_media(integer) from public, anon, authenticated;
revoke execute on function social.cleanup_old_reels_media(integer,integer) from public, anon, authenticated;
