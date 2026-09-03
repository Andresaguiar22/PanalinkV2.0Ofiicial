-- Bucket "profile-covers": portadas de perfil (imagenes pequenas WebP/JPEG/PNG).
-- Mismo patron que "avatars" (20260826010000): publico, limite 2MB, carpeta por uid.
-- Este bucket ya existe en produccion; la migracion lo deja versionado en el repo.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'profile-covers',
  'profile-covers',
  true,
  2097152,
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do nothing;

-- Cada usuario solo escribe dentro de su carpeta {uid}/ (mismo patron que avatars).
drop policy if exists profile_covers_insert on storage.objects;
create policy profile_covers_insert
on storage.objects for insert to authenticated
with check (bucket_id = 'profile-covers' and (storage.foldername(name))[1] = (select auth.uid())::text);

drop policy if exists profile_covers_update on storage.objects;
create policy profile_covers_update
on storage.objects for update to authenticated
using (bucket_id = 'profile-covers' and owner_id = (select auth.uid())::text)
with check (bucket_id = 'profile-covers' and owner_id = (select auth.uid())::text);

drop policy if exists profile_covers_delete on storage.objects;
create policy profile_covers_delete
on storage.objects for delete to authenticated
using (bucket_id = 'profile-covers' and owner_id = (select auth.uid())::text);

-- Lectura publica: el bucket es publico, /storage/v1/object/public/profile-covers/* no requiere policy SELECT.
