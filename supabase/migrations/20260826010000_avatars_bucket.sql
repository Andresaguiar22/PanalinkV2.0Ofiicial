-- Bucket "avatars": fotos de perfil y portadas (imagenes pequenas WebP/JPEG/PNG).
-- Lo pesado (stories, reels, media de chat, notas de voz, musica) sigue en el CDN externo.
-- Limite 2MB por archivo: la app comprime a WebP (~30-100KB) antes de subir.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'avatars',
  'avatars',
  true,
  2097152,
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do nothing;

-- Cada usuario solo escribe dentro de su carpeta {uid}/ (mismo patron que voice-room-covers).
create policy avatars_insert
on storage.objects for insert to authenticated
with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy avatars_update
on storage.objects for update to authenticated
using (bucket_id = 'avatars' and owner_id = (select auth.uid())::text)
with check (bucket_id = 'avatars' and owner_id = (select auth.uid())::text);

create policy avatars_delete
on storage.objects for delete to authenticated
using (bucket_id = 'avatars' and owner_id = (select auth.uid())::text);

-- Lectura publica: el bucket es publico, /storage/v1/object/public/avatars/* no requiere policy SELECT.
