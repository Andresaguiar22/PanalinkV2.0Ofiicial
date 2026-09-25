-- =====================================================================
-- Control de sesión única por cuenta (PanaLink)
-- =====================================================================
-- Objetivo: que una cuenta no pueda estar activa en varios dispositivos a
-- la vez, y avisar al usuario cuando su cuenta se abre en otro equipo.
--
-- IMPORTANTE (paso manual en la dashboard, NO se puede hacer por SQL):
--   Authentication -> Sessions -> "Max sessions per user" = 1
-- Con ese límite, GoTrue revoca la sesión más antigua al iniciar en otro
-- dispositivo; la app detecta el 401 invalid_grant al refrescar y muestra
-- "Tu sesión se cerró porque iniciaste en otro dispositivo".
--
-- Esta migración aporta:
--   1) public.user_devices  -> registro de dispositivos por usuario.
--   2) register_device()         -> la app lo llama al hacer login.
--   3) get_other_active_devices()-> dispositivos activos != al actual.
--   4) touch_device_on_session() -> trigger sobre auth.sessions (respaldo
--      para registrar el dispositivo aunque la app no llame al RPC).
--
-- Todo SECURITY DEFINER + search_path='' + RLS; escritura solo por RPC.
-- =====================================================================

create table if not exists public.user_devices (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    device_id text not null,
    device_name text,
    last_seen_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    unique (user_id, device_id)
);

create index if not exists idx_user_devices_user_id on public.user_devices (user_id);

alter table public.user_devices enable row level security;

-- El usuario solo puede leer sus propios dispositivos. La escritura se hace
-- exclusivamente por RPC (security definer), por eso no hay policy de insert.
drop policy if exists "user_devices_select_own" on public.user_devices;
create policy "user_devices_select_own"
    on public.user_devices
    for select
    to authenticated
    using (user_id = auth.uid());

-- ---------------------------------------------------------------------
-- register_device: upsert del dispositivo actual del usuario autenticado.
-- ---------------------------------------------------------------------
create or replace function public.register_device(
    p_device_id text,
    p_device_name text default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception 'not_authenticated' using errcode = '28000';
    end if;
    if p_device_id is null or length(trim(p_device_id)) = 0 then
        return;
    end if;

    insert into public.user_devices (user_id, device_id, device_name, last_seen_at)
    values (auth.uid(), p_device_id, nullif(trim(p_device_name), ''), now())
    on conflict (user_id, device_id)
    do update set last_seen_at = now(),
                  device_name  = coalesce(excluded.device_name, public.user_devices.device_name);
end;
$$;

revoke all on function public.register_device(text, text) from public;
grant execute on function public.register_device(text, text) to authenticated;

-- ---------------------------------------------------------------------
-- get_other_active_devices: dispositivos del usuario (activos en la última
-- hora) distintos al actual. La app lo llama tras el login para avisar.
-- ---------------------------------------------------------------------
create or replace function public.get_other_active_devices(
    current_device_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    result jsonb;
begin
    if auth.uid() is null then
        raise exception 'not_authenticated' using errcode = '28000';
    end if;

    select coalesce(
        jsonb_agg(
            jsonb_build_object(
                'device_id', device_id,
                'device_name', coalesce(device_name, 'Dispositivo desconocido'),
                'last_seen_at', last_seen_at
            )
        ),
        '[]'::jsonb
    )
    into result
    from (
        select device_id, device_name, last_seen_at
        from public.user_devices
        where user_id = auth.uid()
          and last_seen_at > now() - interval '1 hour'
          and (current_device_id is null or device_id <> current_device_id)
        order by last_seen_at desc
        limit 10
    ) d;

    return result;
end;
$$;

revoke all on function public.get_other_active_devices(text) from public;
grant execute on function public.get_other_active_devices(text) to authenticated;

-- ---------------------------------------------------------------------
-- trigger de respaldo: cada login crea una fila en auth.sessions. Si la app
-- no llamó a register_device (p. ej. login desde el SDK), registramos el
-- dispositivo con el user_agent como identificador/nombre.
-- ---------------------------------------------------------------------
create or replace function public.touch_device_on_session()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_agent text;
begin
    if new.user_id is null then
        return new;
    end if;

    v_agent := coalesce(nullif(trim(new.user_agent), ''), 'Dispositivo desconocido');

    insert into public.user_devices (user_id, device_id, device_name, last_seen_at)
    values (new.user_id, 'ua:' || md5(v_agent), v_agent, now())
    on conflict (user_id, device_id)
    do update set last_seen_at = now(),
                  device_name  = coalesce(excluded.device_name, public.user_devices.device_name);

    return new;
exception
    when others then
        -- El registro de dispositivos nunca debe romper el login.
        return new;
end;
$$;

drop trigger if exists trg_touch_device_on_session on auth.sessions;
create trigger trg_touch_device_on_session
after insert on auth.sessions
for each row
execute function public.touch_device_on_session();

-- ---------------------------------------------------------------------
-- Realtime: el usuario ve cambios en tiempo real (opcional, por si la UI
-- quiere reaccionar). Requiere que la tabla esté en la publicación.
-- ---------------------------------------------------------------------
do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'user_devices'
    ) then
        alter publication supabase_realtime add table public.user_devices;
    end if;
end $$;
