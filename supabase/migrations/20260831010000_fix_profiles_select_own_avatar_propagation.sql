-- FIX CRITICO (2026-08-31): la foto de perfil de usuarios NUEVOS no se propaga a otros usuarios.
-- Causa raiz: la tabla public.profiles tenia RLS activada SOLO con policies INSERT/UPDATE,
-- pero NINGUNA policy SELECT. En PostgreSQL, un UPDATE bajo RLS exige
-- que la fila objetivo pase la policy SELECT: sin ella, PATCH /rest/v1/profiles?id=eq.<uid>
-- devuelve 200 con lista VACIA, no actualiza NINGUNA fila, y la sincronizacion
-- profiles -> public_profiles JAMAS se dispara. Resultado: el dispositivo del propio usuario veia
-- la foto (la app reconstruia el perfil localmente al recibir respuesta vacia), pero los demas 
-- usuarios obtenian avatar_url = NULL de public_profiles y la UI mostraba las iniciales.



-- 1) Policy SELECT para que el dueno pueda ver/actualizar su propia fila via PostgREST.
drop policy if exists profiles_select_own on public.profiles;

create policy profiles_select_own on public.profiles for select to authenticated using (auth.uid() = id);



grant select on public.profiles to authenticated;
grant select on public.profiles to anon;



-- 2) Backfill de avatar_url de profiles con los objetos ya subidos a Storage. La migracion usa
-- el project_id real de Supabase (tivqjfgjdxgzicrridaz) y cubre tanto la convencion nueva
-- (avatar.webp del ProfileEditScreen) como la legacy (profile_avatar.jpg) y el upload directo (other.jpg). 
update public.profiles p
set avatar_url = coalesce(
    p.avatar_url,
    (select ('https://tivqjfgjdxgzicrridaz.supabase.co/storage/v1/object/public/avatars/' || o.name::text)
       from storage.objects o
       where o.bucket_id = 'avatars'
         and o.name in (p.id::text || '/avatar.webp', p.id::text || '/profile_avatar.jpg', '/' || p.id::text || '/other.jpg')
       order by case when o.name like '%avatar.webp' then 0 when o.name like '%profile_avatar%' then 1 else 2 end
       limit 1)
)
where p.avatar_url is null
  and exists (
    select 1 from storage.objects o
    where o.bucket_id = 'avatars'
      and o.name in (p.id::text || '/avatar.webp', p.id::text || '/profile_avatar.jpg', '/' || p.id::text || '/other.jpg')
  );



-- 3b) Backfill ADICIONAL: objetos de avatar NUEVOS (patron canonico "{uid}/profile_avatar.{ext}"
-- desde Onboarding/ProfileEdit) que ya estan en Storage pero cuyos perfiles quedaron sin avatar_url

update public.profiles p
set avatar_url = coalesce(
    p.avatar_url,
    (select ('https://<secret-hidden>.supabase.co/storage/v1/object/public/avatars/' || o.name::text)
       from storage.objects o
       where o.bucket_id = 'avatars'
         and o.name like (p.id::text || '/profile_avatar%')
       order by
         case
           when o.name like '%profile_avatar.webp' then 0
           when o.name like '%profile_avatar.png' then 1
           when o.name like '%profile_avatar.jpg' then 2
           else 3
         end,
         o.created_at desc
       limit 1)
)
where p.avatar_url is null
  and exists (
    select 1 from storage.objects o
    where o.bucket_id = 'avatars'
      and o.name like (p.id::text || '/profile_avatar%')
  );

-- 3) Backfill de la sincronizacion para todos los perfiles existentes (refrescar public_profiles).
insert into public.public_profiles ( id, first_name, last_name, display_name, avatar_url, updated_at)
select id, first_name, last_name, display_name, avatar_url, updated_at
from public.profiles
on conflict ( id) do update set
    first_name = excluded.first_name,
    last_name = excluded.last_name,
    display_name = excluded.display_name,
    avatar_url = excluded.avatar_url,
    updated_at = excluded.updated_at;
