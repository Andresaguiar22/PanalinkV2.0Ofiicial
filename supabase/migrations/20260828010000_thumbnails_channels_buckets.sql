-- Bucket "thumbnails": miniaturas de stories, reels, posts del muro y media de chat.
-- Son JPEGs pequenos (~10-100KB) generados on-device antes de subir.
-- Supabase Storage es la fuente de verdad; el CDN queda como respaldo.
-- Limite 2MB por archivo (las miniativas pesan mucho menos).

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'thumbnails',
  'thumbnails',
  true,
  2097152,
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do nothing;

-- Cada usuario solo escribe dentro de su carpeta {uid}/
create policy thumbnails_insert
on storage.objects for insert to authenticated
with check (bucket_id = 'thumbnails' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy thumbnails_update
on storage.objects for update to authenticated
using (bucket_id = 'thumbnails' and owner_id = (select auth.uid())::text)
with check (bucket_id = 'thumbnails' and owner_id = (select auth.uid())::text);

create policy thumbnails_delete
on storage.objects for delete to authenticated
using (bucket_id = 'thumbnails' and owner_id = (select auth.uid())::text);

-- SELECT necesaria para que los upserts (x-upsert: true) funcionen
create policy thumbnails_select
on storage.objects for select to authenticated
using (bucket_id = 'thumbnails' and (storage.foldername(name))[1] = (select auth.uid())::text);

-- Bucket "channels": avatares y portadas de canales.
-- Imagenes pequenas, mismo patron que avatars/profile-covers.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'channels',
  'channels',
  true,
  2097152,
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do nothing;

create policy channels_insert
on storage.objects for insert to authenticated
with check (bucket_id = 'channels' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy channels_update
on storage.objects for update to authenticated
using (bucket_id = 'channels' and owner_id = (select auth.uid())::text)
with check (bucket_id = 'channels' and owner_id = (select auth.uid())::text);

create policy channels_delete
on storage.objects for delete to authenticated
using (bucket_id = 'channels' and owner_id = (select auth.uid())::text);

create policy channels_select
on storage.objects for select to authenticated
using (bucket_id = 'channels' and (storage.foldername(name))[1] = (select auth.uid())::text);
