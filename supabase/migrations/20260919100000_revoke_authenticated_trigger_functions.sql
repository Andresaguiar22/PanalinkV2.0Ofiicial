-- Lote C: revoke EXECUTE a authenticated de las funciones de TRIGGER (tambien se elimina el default PUBLIC de las 4 del esquema private).
-- Invariante: los triggers los invoca el motor de Postgres con los privilegios del owner (postgres); ningun cliente debe llamar a estas funciones por RPC.
-- Verificado: ninguna aparece como endpoint rest/v1/rpc/* en los APK publicados v1.3.20..v1.3.49.

revoke execute on function public.handle_new_user_profile() from authenticated;
revoke execute on function public.fcm_notify_on_new_message() from authenticated;
revoke execute on function public.messages_instead_of_insert() from authenticated;
revoke execute on function public.update_post_comments_count() from authenticated;
revoke execute on function public.update_post_likes_count() from authenticated;
revoke execute on function public.update_post_shares_count() from authenticated;
revoke execute on function public.voice_room_enforce_owner_limit() from authenticated;
revoke execute on function public.voice_room_seat_integrity() from authenticated;
revoke execute on function social.fn_update_reel_views_count() from authenticated;
revoke execute on function social.handle_reel_report() from authenticated;

-- Las 4 del esquema private tienen proacl='(default)' -> EXECUTE a PUBLIC por defecto.
-- Eliminar el heredado. El owner es postgres: el trigger seguira invocandolas sin problemas.

revoke execute on function private.trg_posts_vcdn_delete() from public;
revoke execute on function private.trg_posts_vcdn_delete() from authenticated;
revoke execute on function private.trg_reels_vcdn_delete() from public;
revoke execute on function private.trg_reels_vcdn_delete() from authenticated;
revoke execute on function private.trg_stories_vcdn_delete() from public;
revoke execute on function private.trg_stories_vcdn_delete() from authenticated;
revoke execute on function private.trg_thread_msgs_vcdn_delete_fn() from public;
revoke execute on function private.trg_thread_msgs_vcdn_delete_fn() from authenticated;