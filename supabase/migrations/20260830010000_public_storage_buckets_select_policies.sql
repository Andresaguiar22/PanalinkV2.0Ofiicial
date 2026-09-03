-- FIX CRITICO (2026-08-30): Permitir SELECT publico/autenticado en buckets publicos de storage.
-- Anteriormente, las policies SELECT estaban restringidas a (storage.foldername(name))[1] = auth.uid(),
-- lo que impedia que otros usuarios leyeran avatares, portadas y miniaturas, causando errores 403/400 (Coil mostraba iniciales).

drop policy if exists avatars_select on storage.objects;
create policy avatars_select
on storage.objects for select to public, authenticated
using (bucket_id = 'avatars');

drop policy if exists profile_covers_select on storage.objects;
create policy profile_covers_select
on storage.objects for select to public, authenticated
using (bucket_id = 'profile-covers');

drop policy if exists voice_room_covers_select on storage.objects;
create policy voice_room_covers_select
on storage.objects for select to public, authenticated
using (bucket_id = 'voice-room-covers');

drop policy if exists thumbnails_select on storage.objects;
create policy thumbnails_select
on storage.objects for select to public, authenticated
using (bucket_id = 'thumbnails');

drop policy if exists channels_select on storage.objects;
create policy channels_select
on storage.objects for select to public, authenticated
using (bucket_id = 'channels');
