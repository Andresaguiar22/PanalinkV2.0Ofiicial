-- Lote E: revoke EXECUTE a authenticated de las 28 funciones legacy MUERTAS (29 registros por un overload)..
-- Verificacion previa: 0 referencias en APK v1.3.20..v1.3.49 (dex), 0 callers en pg_proc.prosrc, 0 policies, 0 edge functions.

revoke execute on function public.add_contact_by_pin(text) from authenticated;
revoke execute on function public.add_sticker_to_pack(uuid,uuid,integer) from authenticated;
revoke execute on function public.assign_seat(uuid,uuid,integer) from authenticated;
revoke execute on function public.ban_user(uuid,uuid,text) from authenticated;
revoke execute on function public.close_voice_room(uuid) from authenticated;
revoke execute on function public.ensure_one_to_one_thread(uuid,uuid) from authenticated;
revoke execute on function public.is_chat_member(uuid,uuid) from authenticated;
revoke execute on function public.is_voice_room_member(uuid) from authenticated;
revoke execute on function public.kick_user(uuid,uuid) from authenticated;
revoke execute on function public.mark_1_to_1_as_read(uuid,uuid) from authenticated;
revoke execute on function public.markthreaddelivered(uuid) from authenticated;
revoke execute on function public.markthreadread(uuid) from authenticated;
revoke execute on function public.mute_user(uuid,uuid) from authenticated;
revoke execute on function public.remove_from_seat(uuid,uuid) from authenticated;
revoke execute on function public.remove_sticker_from_pack(uuid,uuid) from authenticated;
revoke execute on function public.request_seat(uuid) from authenticated;
revoke execute on function public.resolve_contact_identifier(text) from authenticated;
revoke execute on function public.send_1_to_1_message_auto(uuid,uuid,uuid,uuid,text,text,text,text,text) from authenticated;
revoke execute on function public.send_1_to_1_message_auto(uuid,uuid,uuid,uuid,text,text,text,text,text,integer,integer,text) from authenticated;
revoke execute on function public.soft_delete_sticker(uuid) from authenticated;
revoke execute on function public.soft_delete_sticker_pack(uuid) from authenticated;
revoke execute on function public.thread_allows_user(uuid,uuid) from authenticated;
revoke execute on function public.toggle_private_room(uuid,boolean) from authenticated;
revoke execute on function public.toggle_room_lock(uuid,boolean) from authenticated;
revoke execute on function public.unban_user(uuid,uuid) from authenticated;
revoke execute on function public.unmute_user(uuid,uuid) from authenticated;
revoke execute on function public.unsave_sticker(uuid) from authenticated;
revoke execute on function social.toggle_favorite(uuid,boolean) from authenticated;
revoke execute on function social.toggle_like(uuid,boolean) from authenticated;