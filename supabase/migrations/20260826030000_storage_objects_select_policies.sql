-- FIX CRITICO (2026-08-26): upserts a storage.objects fallaban con
-- "new row violates row-level security policy" (Storage API HTTP 400).
-- Causa raiz: INSERT ... ON CONFLICT DO UPDATE (lo que hace x-upsert=true / PUT)
-- exige que la fila en conflicto sea visible via policies SELECT. Sin policy
-- SELECT para authenticated, TODO upsert falla aunque insert/update esten bien.
-- Esto mataba avatares, portadas de perfil y covers de salas de voz.

drop policy if exists avatars_select on storage.objects;
create policy avatars_select
on storage.objects for select to authenticated
using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists profile_covers_select on storage.objects;
create policy profile_covers_select
on storage.objects for select to authenticated
using (bucket_id = 'profile-covers' and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists voice_room_covers_select on storage.objects;
create policy voice_room_covers_select
on storage.objects for select to authenticated
using (bucket_id = 'voice-room-covers' and (storage.foldername(name))[1] = (select auth.uid())::text);
