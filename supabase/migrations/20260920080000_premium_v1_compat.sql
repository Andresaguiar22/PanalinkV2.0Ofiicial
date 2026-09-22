-- =============================================================================
-- PanaLink Premium 2.0 — Adaptación de schema v1 (legacy) a v2
-- -----------------------------------------------------------------------------
-- En producción existen 3 tablas del sistema premium v1 (live_engagement/gifts y
-- entitlements legacy):
--   * premium_features(code, is_sensitive, default_enabled)     -- 22 filas reales
--   * user_entitlements(user_id, feature_code, enabled, expires_at, updated_at) -- 0 filas
--   * user_wallets(user_id, coins, updated_at)                  -- saldos reales
--
-- El núcleo Premium 2.0 (20260921000000) espera el schema v2:
--   * premium_features(feature_key, display_name, description, icon, enabled, ...)
--   * user_entitlements(id, user_id, product_code, feature_key, starts_at, expires_at,
--                       status, renewed_from, created_at)  -- PK por id (no compuesta)
--   * user_wallets, que la core ya extiende con ADD COLUMN IF NOT EXISTS.
--
-- Esta migración alinea las tablas v1 al schema v2 ANTES de que corra la core,
-- conservando los datos v1 (mapeados) y sin romper la compatibilidad de lectura
-- de la app v1 (feature_code/enabled siguen existiendo en entitlements).
--
-- Idempotente: los cambios usan ADD COLUMN IF NOT EXISTS / DROP CONSTRAINT IF EXISTS.
-- =============================================================================

-- =============================================================================
-- 1) premium_features: code -> feature_key, default_enabled -> enabled
--    Se conservan is_sensitive y todas las filas (22).
-- =============================================================================

-- Renombra columnas solo si aún existen con el nombre v1.
do $$
begin
    if exists (select 1 from information_schema.columns
               where table_schema = 'public' and table_name = 'premium_features'
                 and column_name = 'code') then
        alter table public.premium_features rename column code to feature_key;
    end if;
    if exists (select 1 from information_schema.columns
               where table_schema = 'public' and table_name = 'premium_features'
                 and column_name = 'default_enabled') then
        alter table public.premium_features rename column default_enabled to enabled;
    end if;
end $$;

-- Añade las columnas v2 que faltan (con la PK de feature_key preservada por el rename).
alter table public.premium_features
    add column if not exists display_name text,
    add column if not exists description text,
    add column if not exists icon text,
    add column if not exists minimum_level integer not null default 0,
    add column if not exists trial_days integer not null default 0,
    add column if not exists sort_order integer not null default 0,
    add column if not exists created_at timestamptz not null default now(),
    add column if not exists updated_at timestamptz not null default now();

-- Backfill de las columnas nuevas a partir de los datos v1 ya presentes.
update public.premium_features
set display_name = coalesce(display_name, feature_key),
    icon = coalesce(icon, '✨')
where display_name is null or icon is null;

-- =============================================================================
-- 2) user_entitlements: añade columnas v2 y migra la PK compuesta v1 a id uuid.
--    Tabla vacía en prod (0 filas) => no hay datos que perder.
--    Se MANTIENEN feature_code / enabled / updated_at para compat de la app v1.
-- =============================================================================

do $$
begin
    -- La PK compuesta v1 (user_id, feature_code) impide los INSERTs v2 que no traen
    -- feature_code. Como la tabla está vacía, se reemplaza por la PK id v2.
    if exists (select 1 from pg_constraint
               where conname = 'user_entitlements_pkey'
                 and conrelid = 'public.user_entitlements'::regclass) then
        alter table public.user_entitlements drop constraint user_entitlements_pkey;
    end if;
    -- La FK a premium_features(feature_code) debe apuntar a feature_key tras el rename.
    -- PostgreSQL la actualiza automáticamente al renombrar; aseguramos que exista la columna.
    if not exists (select 1 from information_schema.columns
                   where table_schema = 'public' and table_name = 'user_entitlements'
                     and column_name = 'feature_code') then
        alter table public.user_entitlements add column feature_code text;
    end if;
end $$;

alter table public.user_entitlements
    add column if not exists id uuid not null default gen_random_uuid(),
    add column if not exists product_code text,
    add column if not exists feature_key text,
    add column if not exists starts_at timestamptz not null default now(),
    add column if not exists status text not null default 'active',
    add column if not exists renewed_from uuid,
    add column if not exists created_at timestamptz not null default now();

-- Las columnas v1 (feature_code, enabled) eran NOT NULL sin default la core v2
-- no las escribe. Se hacen nullable para que sus INSERTs no fallen; la app v1
-- sigue leyéndolas (NULL => no activa en el sistema legacy, comportamiento correcto).
alter table public.user_entitlements
    alter column feature_code drop not null,
    alter column enabled drop not null;

-- PK v2 sobre id (no puede existir hasta que se haya añadido la columna).
do $$
begin
    if not exists (select 1 from pg_constraint
                   where conname = 'user_entitlements_pkey'
                     and conrelid = 'public.user_entitlements'::regclass) then
        alter table public.user_entitlements add constraint user_entitlements_pkey primary key (id);
    end if;
end $$;

-- Backfill: alinea feature_key/status/starts_at con los valores v1 existentes.
update public.user_entitlements
set feature_key = coalesce(feature_key, feature_code),
    status      = coalesce(status, case when enabled then 'active' else 'expired' end),
    starts_at   = coalesce(starts_at, updated_at, now());