-- Pendant (colgante/marco de avatar) personal del usuario.
-- NOTA Estado: los marcos son gratis por ahora (sin tienda). El usuario elige su
-- colgante en el toolbox de cualquier sala y este se replica en TODAS las salas
-- a las que entre (el perfil viaja con el usuario, no con la sala).
--
-- 1) Columna en `profiles` (propietario): la app escribe aqui (RLS update self).
-- 2) Columna en `public_profiles` (vista pública): todos los usuarios leen aqui.
-- 3) El trigger de sync existente se amplia para copiar pendant_code.

alter table public.profiles
    add column if not exists pendant_code text not null default 'none';

alter table public.public_profiles
    add column if not exists pendant_code text not null default 'none';

-- Ampliar la funcion de sync para que propague pendant_code.
create or replace function public.sync_profile_to_public_profile()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public
as $$
begin
    if (tg_op = 'DELETE') then
        delete from public.public_profiles where id = old.id;
        return old;
    elsif (tg_op = 'INSERT' or tg_op = 'UPDATE') then
        insert into public.public_profiles (
            id,
            display_name,
            first_name,
            last_name,
            avatar_url,
            pendant_code,
            updated_at
        ) values (
            new.id,
            new.display_name,
            new.first_name,
            new.last_name,
            new.avatar_url,
            new.pendant_code,
            new.updated_at
        )
        on conflict (id) do update set
            display_name = excluded.display_name,
            first_name = excluded.first_name,
            last_name = excluded.last_name,
            avatar_url = excluded.avatar_url,
            pendant_code = excluded.pendant_code,
            updated_at = excluded.updated_at;
        return new;
    end if;
    return null;
end;
$$;

revoke execute on function public.sync_profile_to_public_profile() from public, anon, authenticated;

-- Backfill: copiar a los perfiles publicos existentes que ya tienen columna.
update public.public_profiles p
set pendant_code = prof.pendant_code
from public.profiles prof
where prof.id = p.id;