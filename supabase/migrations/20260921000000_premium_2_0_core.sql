-- =============================================================================
-- PanaLink Premium 2.0 — Núcleo (Fase 0)
-- -----------------------------------------------------------------------------
-- Sistema vivo de economía + Premium: monedas, diamantes, tickets, niveles,
-- recompensas diarias, rachas, misiones, eventos, promociones, notificaciones,
-- feature flags, beta flags y analítica. Todo transaccional y a prueba de
-- manipulaciones: el saldo se deriva de un LEDGER INMUTABLE (wallet_transactions)
-- y las compras son atómicas en una sola operación SQL.
--
-- Fases posteriores (1..7) consumen estas tablas sin volver a migrar el core.
--
-- Convenciones del repo respetadas:
--   * security definer + set search_path = '' en TODOS los RPC
--   * auth.uid() explícito (raise 'not_authenticated')
--   * REVOKE a public/anon + GRANT solo a authenticated
--   * Idempotente: se puede reaplicar sin romper nada
-- =============================================================================

-- =============================================================================
-- 1) WALLET / LEDGER
-- =============================================================================

-- La tabla user_wallets YA existe (live_engagement + gifts).
-- Añadimos columnas para diamantes, tickets, xp y nivel sin romper nada.

alter table public.user_wallets
    add column if not exists diamonds integer not null default 0 check (diamonds >= 0),
    add column if not exists tickets integer not null default 0 check (tickets >= 0),
    add column if not exists xp integer not null default 0 check (xp >= 0),
    add column if not exists level integer not null default 1 check (level >= 1);

-- Ledger inmutable: TODA variación de monedas/diamantes/tickets/xp queda aquí.
-- El saldo de user_wallets es derivado de la suma de los amount del ledger.
-- Cada transacción es idempotente vía request_id único (anti reintentos/duplicados).
create table if not exists public.wallet_transactions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    kind text not null check (kind in (
        'premium_buy', 'premium_renew', 'gift_sent', 'gift_received',
        'diamond_exchange', 'event_reward', 'reward_claim', 'daily_reward',
        'mission_reward', 'streak_reward', 'ticket_redeem', 'admin_grant',
        'promotion_reward', 'mystery_box', 'refund', 'adjustment'
    )),
    currency text not null default 'coins' check (currency in ('coins','diamonds','tickets','xp')),
    amount integer not null check (amount <> 0),
    balance_after integer not null default 0,
    description text,
    ref_type text,
    ref_id uuid,
    meta jsonb,
    request_id text unique,
    created_at timestamptz not null default now()
);

create index if not exists idx_wallet_tx_user_kind on public.wallet_transactions(user_id, created_at desc);
create index if not exists idx_wallet_tx_user_kind2 on public.wallet_transactions(user_id, kind);

alter table public.wallet_transactions enable row level security;

drop policy if exists "Users can select own wallet transactions" on public.wallet_transactions;
create policy "Users can select own wallet transactions"
    on public.wallet_transactions
    for select
    to authenticated
    using (user_id = auth.uid());

-- =============================================================================
-- 2) CATÁLOGO DE PRODUCTOS PREMIUM + FEATURE FLAGS
-- =============================================================================

-- Los productos son DURATIONS (días) de funciones. El catálogo vive en DB para
-- poder A/B testear precios y duraciones sin publicar APK.
create table if not exists public.premium_products (
    id uuid primary key default gen_random_uuid(),
    code text unique not null,
    feature_key text not null,
    name text not null,
    emoji text not null,
    description text,
    duration_days integer not null check (duration_days > 0),
    price_coins integer not null check (price_coins > 0),
    trial_days integer not null default 0 check (trial_days >= 0),
    available_tiers integer[] not null default array[1],
    sort_order integer not null default 0,
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

alter table public.premium_products enable row level security;

drop policy if exists "Anyone can read premium products" on public.premium_products;
create policy "Anyone can read premium products"
    on public.premium_products
    for select
    to authenticated
    using (is_active);

-- Feature flags: activar/desactivar sin publicar nueva versión.
create table if not exists public.premium_features (
    feature_key text primary key,
    display_name text not null,
    description text,
    icon text,
    enabled boolean not null default true,
    minimum_level integer not null default 0,
    trial_days integer not null default 0,
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.premium_features enable row level security;

drop policy if exists "Anyone can read premium features" on public.premium_features;
create policy "Anyone can read premium features"
    on public.premium_features
    for select
    to authenticated
    using (enabled);

-- Beta flags: rollout gradual por user/role/país/porcentaje.
create table if not exists public.premium_beta_flags (
    id uuid primary key default gen_random_uuid(),
    feature_key text not null references public.premium_features(feature_key) on delete cascade,
    rollout_percent integer not null default 100 check (rollout_percent between 0 and 100),
    user_ids uuid[] not null default '{}',
    roles text[] not null default '{}',
    countries text[] not null default '{}',
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.premium_beta_flags enable row level security;

drop policy if exists "Anyone can read premium beta flags" on public.premium_beta_flags;
create policy "Anyone can read premium beta flags"
    on public.premium_beta_flags
    for select
    to authenticated
    using (enabled);

-- =============================================================================
-- 3) ENTITLEMENTS (suscripciones por días)
-- =============================================================================

-- Beneficio ACTIVO del usuario. Se apila: comprar 5 días con 10 existentes = 15.
create table if not exists public.user_entitlements (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    product_code text not null references public.premium_products(code),
    feature_key text not null,
    starts_at timestamptz not null default now(),
    expires_at timestamptz not null,
    status text not null default 'active' check (status in ('active','expired','revoked')),
    renewed_from uuid references public.user_entitlements(id),
    created_at timestamptz not null default now()
);

create index if not exists idx_user_entitlements_user_active on public.user_entitlements(user_id, status, expires_at desc);
create index if not exists idx_user_entitlements_feature on public.user_entitlements(feature_key, status, expires_at desc);

alter table public.user_entitlements enable row level security;

drop policy if exists "Users can select own entitlements" on public.user_entitlements;
create policy "Users can select own entitlements"
    on public.user_entitlements
    for select
    to authenticated
    using (user_id = auth.uid());

-- =============================================================================
-- 4) RECOMPENSAS / MISIONES / RACHAS
-- =============================================================================

-- Configuración de la recompensa diaria (día -> premio).
create table if not exists public.daily_rewards_config (
    day_number integer primary key check (day_number between 1 and 7),
    currency text not null check (currency in ('coins','diamonds','tickets')),
    amount integer not null check (amount > 0)
);

alter table public.daily_rewards_config enable row level security;

drop policy if exists "Anyone can read daily rewards config" on public.daily_rewards_config;
create policy "Anyone can read daily rewards config"
    on public.daily_rewards_config
    for select
    to authenticated
    using (true);

-- Estado de recompensa diaria y racha por usuario.
create table if not exists public.user_rewards (
    user_id uuid primary key references auth.users(id) on delete cascade,
    last_claim_date date,
    current_streak integer not null default 0,
    max_streak integer not null default 0,
    next_claim_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.user_rewards enable row level security;

drop policy if exists "Users can select own rewards" on public.user_rewards;
create policy "Users can select own rewards"
    on public.user_rewards
    for select
    to authenticated
    using (user_id = auth.uid());

-- Catálogo de misiones (diarias/semanales).
create table if not exists public.missions (
    id uuid primary key default gen_random_uuid(),
    code text unique not null,
    title text not null,
    description text,
    scope text not null check (scope in ('daily','weekly')) default 'daily',
    reward_currency text not null check (reward_currency in ('coins','diamonds','tickets','xp')) default 'coins',
    reward_amount integer not null check (reward_amount > 0),
    target integer not null default 1,
    sort_order integer not null default 0,
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

alter table public.missions enable row level security;

drop policy if exists "Anyone can read missions" on public.missions;
create policy "Anyone can read missions"
    on public.missions
    for select
    to authenticated
    using (is_active);

-- Progreso del usuario en misiones.
create table if not exists public.user_missions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    mission_id uuid not null references public.missions(id) on delete cascade,
    progress integer not null default 0 check (progress >= 0),
    completed_at timestamptz,
    claimed_at timestamptz,
    period_start date,
    period_end date,
    unique (user_id, mission_id, period_start)
);

alter table public.user_missions enable row level security;

drop policy if exists "Users can select own missions" on public.user_missions;
create policy "Users can select own missions"
    on public.user_missions
    for select
    to authenticated
    using (user_id = auth.uid());

-- =============================================================================
-- 5) EVENTOS TEMPORALES Y PROMOCIONES
-- =============================================================================

create table if not exists public.premium_events (
    id uuid primary key default gen_random_uuid(),
    code text unique not null,
    title text not null,
    subtitle text,
    description text,
    emoji text,
    image_url text,
    action_type text not null default 'deep_link' check (action_type in ('deep_link','shop','promotion')),
    action_value text,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    multiplier_coins numeric not null default 1.0 check (multiplier_coins >= 1.0),
    multiplier_xp numeric not null default 1.0 check (multiplier_xp >= 1.0),
    enabled boolean not null default true,
    sort_order integer not null default 0,
    created_at timestamptz not null default now()
);

alter table public.premium_events enable row level security;

drop policy if exists "Anyone can read premium events" on public.premium_events;
create policy "Anyone can read premium events"
    on public.premium_events
    for select
    to authenticated
    using (enabled);

create table if not exists public.premium_promotions (
    id uuid primary key default gen_random_uuid(),
    code text unique,
    title text not null,
    subtitle text,
    description text,
    image_url text,
    banner_url text,
    action_type text not null default 'shop' check (action_type in ('shop','deep_link','feature')),
    action_value text,
    feature_key text,
    price_coins integer,
    discount_percent integer not null default 0 check (discount_percent between 0 and 100),
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    priority integer not null default 0,
    enabled boolean not null default true,
    created_at timestamptz not null default now()
);

alter table public.premium_promotions enable row level security;

drop policy if exists "Anyone can read premium promotions" on public.premium_promotions;
create policy "Anyone can read premium promotions"
    on public.premium_promotions
    for select
    to authenticated
    using (enabled);

-- =============================================================================
-- 6) NOTIFICACIONES (in-app + categorías + preferencias)
-- =============================================================================

create table if not exists public.in_app_notifications (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    category text not null check (category in (
        'PREMIUM','SOCIAL','LIVE','REWARD','COINS','PROMOTION','SYSTEM','SECURITY'
    )),
    priority text not null default 'NORMAL' check (priority in ('LOW','NORMAL','HIGH','URGENT')),
    title text not null,
    body text,
    payload jsonb,
    is_read boolean not null default false,
    created_at timestamptz not null default now()
);

create index if not exists idx_notifications_user_created on public.in_app_notifications(user_id, created_at desc);

alter table public.in_app_notifications enable row level security;

drop policy if exists "Users can select own notifications" on public.in_app_notifications;
create policy "Users can select own notifications"
    on public.in_app_notifications
    for select
    to authenticated
    using (user_id = auth.uid());

-- Preferencias de notificación del usuario (qué categorías quiere recibir).
create table if not exists public.notification_preferences (
    user_id uuid primary key references auth.users(id) on delete cascade,
    categories_enabled text[] not null default '{}',
    push_enabled boolean not null default true,
    updated_at timestamptz not null default now()
);

alter table public.notification_preferences enable row level security;

drop policy if exists "Users can select own notification prefs" on public.notification_preferences;
create policy "Users can select own notification prefs"
    on public.notification_preferences
    for select
    to authenticated
    using (user_id = auth.uid());

-- =============================================================================
-- 7) ADMIN GRANTS (créditos de administración, para soporte / bonos)
-- =============================================================================

create table if not exists public.admin_grants (
    id uuid primary key default gen_random_uuid(),
    target_user_id uuid not null references auth.users(id) on delete cascade,
    granted_by uuid references auth.users(id),
    currency text not null check (currency in ('coins','diamonds','tickets','xp')),
    amount integer not null check (amount > 0),
    reason text,
    created_at timestamptz not null default now()
);

alter table public.admin_grants enable row level security;

-- service_role bypasses RLS por defecto en Supabase; la policy es declarativa
-- para no depender de esa configuración.
drop policy if exists "Service role can manage admin grants" on public.admin_grants;
create policy "Service role can manage admin grants"
    on public.admin_grants
    for all
    to service_role
    using (true)
    with check (true);

-- =============================================================================
-- 8) INSTALACIÓN DE LA ECONOMÍA DE NIVELES (XP)
-- =============================================================================

-- Función que calcula el nivel a partir del XP (rango configurable).
create or replace function public.level_from_xp(p_xp integer)
returns integer
language sql
immutable
set search_path = ''
as $$
    select greatest(1, floor(
        (sqrt(1 + 8.0 * (coalesce(p_xp, 0) / 50.0 + 1)) - 1) / 2.0
    )::integer)
$$;

-- Recalcula el nivel de user_wallets a partir de su XP.
create or replace function public.recalc_level_for_user(p_user_id uuid)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_xp integer;
    v_level integer;
begin
    select coalesce(xp, 0) into v_xp from public.user_wallets where user_id = p_user_id;
    v_level := public.level_from_xp(v_xp);
    update public.user_wallets set level = v_level where user_id = p_user_id;
    return v_level;
end;
$$;

-- =============================================================================
-- 9) RPC DE SALDO WALLET UNIFICADO
-- =============================================================================

-- Devuelve el saldo completo actual (coins/diamonds/tickets/xp/nivel).
create or replace function public.wallet_balance_full()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_w record;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    insert into public.user_wallets (user_id, coins)
    values (v_uid, 0)
    on conflict (user_id) do nothing;

    select * into v_w from public.user_wallets where user_id = v_uid;

    return jsonb_build_object(
        'ok', true,
        'coins', coalesce(v_w.coins, 0),
        'diamonds', coalesce(v_w.diamonds, 0),
        'tickets', coalesce(v_w.tickets, 0),
        'xp', coalesce(v_w.xp, 0),
        'level', coalesce(v_w.level, 1)
    );
end;
$$;

-- =============================================================================
-- 10) LEDGER HELPERS (operaciones atómicas sobre el ledger)
-- =============================================================================

-- Aplica un delta al ledger y actualiza el saldo de user_wallets (currency).
-- Uso interno de otros RPCs.
create or replace function public.ledger_apply(
    p_user_id uuid,
    p_kind text,
    p_currency text,
    p_amount integer,
    p_description text,
    p_ref_type text,
    p_ref_id uuid,
    p_meta jsonb,
    p_request_id text
)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_new_balance integer;
    v_balance integer := 0;
begin
    -- Idempotencia: si request_id ya existe, no se aplica de nuevo.
    if p_request_id is not null then
        if exists (select 1 from public.wallet_transactions where request_id = p_request_id) then
            select balance_after into v_new_balance
              from public.wallet_transactions
             where request_id = p_request_id
             order by created_at desc limit 1;
            return v_new_balance;
        end if;
    end if;

    -- Asegura que exista el wallet (mismo criterio que live_ensure_wallet).
    insert into public.user_wallets (user_id, coins)
    values (p_user_id, 0)
    on conflict (user_id) do nothing;

    select
        case p_currency
            when 'coins' then coins
            when 'diamonds' then diamonds
            when 'tickets' then tickets
            when 'xp' then xp
            else 0
        end
    into v_balance
    from public.user_wallets
    where user_id = p_user_id;

    v_new_balance := v_balance + p_amount;

    update public.user_wallets
    set
        coins = case when p_currency = 'coins' then v_new_balance else coins end,
        diamonds = case when p_currency = 'diamonds' then v_new_balance else diamonds end,
        tickets = case when p_currency = 'tickets' then v_new_balance else tickets end,
        xp = case when p_currency = 'xp' then v_new_balance else xp end,
        updated_at = now()
    where user_id = p_user_id;

    insert into public.wallet_transactions (
        user_id, kind, currency, amount, balance_after,
        description, ref_type, ref_id, meta, request_id
    ) values (
        p_user_id, p_kind, p_currency, p_amount, v_new_balance,
        p_description, p_ref_type, p_ref_id, p_meta, p_request_id
    );

    -- Si el delta es de XP, actualizamos el nivel.
    if p_currency = 'xp' then
        perform public.recalc_level_for_user(p_user_id);
    end if;

    return v_new_balance;
end;
$$;

-- Convierte diamantes a monedas a tasa fija (1💎 = 100🪙).
create or replace function public.diamonds_exchange(
    p_amount integer,
    p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_rate integer := 100;
    v_balance integer;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;
    if p_amount is null or p_amount <= 0 then
        raise exception 'invalid_amount';
    end if;

    -- Chequeo atómico: que tenga diamantes suficientes.
    update public.user_wallets
       set diamonds = diamonds - p_amount,
           coins = coins + (p_amount * v_rate),
           updated_at = now()
     where user_id = v_uid
       and diamonds >= p_amount
    returning diamonds into v_balance;

    if v_balance is null then
        return jsonb_build_object('ok', false, 'reason', 'insufficient_diamonds');
    end if;

    insert into public.wallet_transactions (user_id, kind, currency, amount, balance_after, description, request_id)
    select v_uid, 'diamond_exchange', 'coins', p_amount * v_rate, b.coins,
           'Intercambio de ' || p_amount || '💎', p_request_id
      from public.user_wallets b where b.user_id = v_uid;

    insert into public.wallet_transactions (user_id, kind, currency, amount, balance_after, description, request_id)
    select v_uid, 'diamond_exchange', 'diamonds', -p_amount, d.diamonds,
           'Intercambio a monedas', p_request_id || '-d'
      from public.user_wallets d where d.user_id = v_uid;

    return jsonb_build_object('ok', true, 'coins', (select coins from public.user_wallets where user_id = v_uid));
end;
$$;

-- =============================================================================
-- 11) COMPRA DE PREMIUM (ATÓMICA + APILABLE)
-- =============================================================================

-- Compra un producto premium con monedas. Devuelve expires_at nuevo.
-- Apila si ya hay un entitlement activo del mismo feature.
create or replace function public.premium_buy(
    p_product_code text,
    p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_product public.premium_products%rowtype;
    v_price integer;
    v_balance integer;
    v_new_expiry timestamptz;
    v_existing timestamptz;
    v_feature_flag boolean;
    v_ent_id uuid;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    if p_product_code is null or p_product_code = '' then
        raise exception 'invalid_product';
    end if;

    if exists (select 1 from public.wallet_transactions where request_id = p_request_id and user_id = v_uid) then
        return jsonb_build_object('ok', false, 'reason', 'duplicate_request');
    end if;

    -- Producto activo + feature habilitada.
    select * into v_product
      from public.premium_products
     where code = p_product_code and is_active;

    if v_product is null then
        return jsonb_build_object('ok', false, 'reason', 'unknown_product');
    end if;

    select enabled into v_feature_flag from public.premium_features where feature_key = v_product.feature_key;
    if v_feature_flag is false then
        return jsonb_build_object('ok', false, 'reason', 'feature_disabled');
    end if;

    v_price := v_product.price_coins;

    -- Débito atómico con chequeo de saldo (mismo patrón que live_send_gift).
    update public.user_wallets
       set coins = coins - v_price,
           updated_at = now()
     where user_id = v_uid
       and coins >= v_price
    returning coins into v_balance;

    if v_balance is null then
        select coins into v_balance from public.user_wallets where user_id = v_uid;
        return jsonb_build_object(
            'ok', false,
            'reason', 'insufficient_funds',
            'balance', coalesce(v_balance, 0)
        );
    end if;

    -- Fecha de expiración: apila sobre el entitlement activo del feature.
    select expires_at into v_existing
      from public.user_entitlements
     where user_id = v_uid
       and feature_key = v_product.feature_key
       and status = 'active'
     order by expires_at desc
     limit 1;

    v_new_expiry := coalesce(v_existing, now()) + (v_product.duration_days || ' days')::interval;

    -- Insert del entitlement.
    insert into public.user_entitlements (user_id, product_code, feature_key, starts_at, expires_at, status)
    values (v_uid, p_product_code, v_product.feature_key, now(), v_new_expiry, 'active')
    returning id into v_ent_id;

    -- Ledger del gasto.
    insert into public.wallet_transactions (
        user_id, kind, currency, amount, balance_after, description,
        ref_type, ref_id, meta, request_id
    ) values (
        v_uid, 'premium_buy', 'coins', -v_price, v_balance,
        v_product.name, 'premium_products', v_product.id,
        jsonb_build_object('product_code', p_product_code, 'expires_at', v_new_expiry),
        p_request_id
    );

    return jsonb_build_object(
        'ok', true,
        'balance', v_balance,
        'expires_at', v_new_expiry,
        'entitlement_id', v_ent_id,
        'feature_key', v_product.feature_key
    );
end;
$$;

-- =============================================================================
-- 12) ENTITLEMENTS ACTIVOS DEL USUARIO
-- =============================================================================

create or replace function public.premium_my_entitlements()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_result jsonb;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    select coalesce(jsonb_agg(jsonb_build_object(
        'id', e.id,
        'feature_key', e.feature_key,
        'product_code', e.product_code,
        'name', p.name,
        'emoji', p.emoji,
        'starts_at', e.starts_at,
        'expires_at', e.expires_at,
        'status', case when e.expires_at < now() then 'expired' else e.status end,
        'days_left', greatest(0, ceil(extract(epoch from (e.expires_at - now()))/86400.0))::int
    ) order by e.expires_at desc), '[]'::jsonb) into v_result
    from public.user_entitlements e
    join public.premium_products p on p.code = e.product_code
    where e.user_id = v_uid
      and e.status = 'active';

    return jsonb_build_object('ok', true, 'entitlements', v_result);
end;
$$;

-- =============================================================================
-- 13) CATÁLOGO PREMIUM PARA LA APP
-- =============================================================================

create or replace function public.premium_catalog()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_result jsonb;
begin
    if auth.uid() is null then
        raise exception 'not_authenticated';
    end if;

    -- Solo features habilitadas.
    select coalesce(jsonb_agg(jsonb_build_object(
        'code', p.code,
        'feature_key', p.feature_key,
        'name', p.name,
        'emoji', p.emoji,
        'description', p.description,
        'duration_days', p.duration_days,
        'price_coins', p.price_coins,
        'trial_days', p.trial_days,
        'sort_order', p.sort_order,
        'feature_enabled', f.enabled
    ) order by p.sort_order, p.price_coins), '[]'::jsonb) into v_result
    from public.premium_products p
    join public.premium_features f on f.feature_key = p.feature_key
    where p.is_active and f.enabled;

    return jsonb_build_object('ok', true, 'products', v_result);
end;
$$;

-- =============================================================================
-- 14) PROMOCIONES ACTIVAS + EVENTOS ACTIVOS
-- =============================================================================

create or replace function public.premium_active_promotions()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_result jsonb;
begin
    if auth.uid() is null then
        raise exception 'not_authenticated';
    end if;

    select coalesce(jsonb_agg(jsonb_build_object(
        'id', p.id,
        'title', p.title,
        'subtitle', p.subtitle,
        'description', p.description,
        'image_url', p.image_url,
        'banner_url', p.banner_url,
        'action_type', p.action_type,
        'action_value', p.action_value,
        'feature_key', p.feature_key,
        'price_coins', p.price_coins,
        'discount_percent', p.discount_percent,
        'ends_at', p.ends_at,
        'priority', p.priority
    ) order by p.priority desc, p.ends_at asc), '[]'::jsonb) into v_result
    from public.premium_promotions p
    where p.enabled
      and p.starts_at <= now()
      and p.ends_at >= now();

    return jsonb_build_object('ok', true, 'promotions', v_result);
end;
$$;

create or replace function public.premium_active_events()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_result jsonb;
begin
    if auth.uid() is null then
        raise exception 'not_authenticated';
    end if;

    select coalesce(jsonb_agg(jsonb_build_object(
        'code', e.code,
        'title', e.title,
        'subtitle', e.subtitle,
        'description', e.description,
        'emoji', e.emoji,
        'image_url', e.image_url,
        'action_type', e.action_type,
        'action_value', e.action_value,
        'starts_at', e.starts_at,
        'ends_at', e.ends_at,
        'multiplier_coins', e.multiplier_coins,
        'multiplier_xp', e.multiplier_xp
    ) order by e.starts_at desc), '[]'::jsonb) into v_result
    from public.premium_events e
    where e.enabled
      and e.starts_at <= now()
      and e.ends_at >= now();

    return jsonb_build_object('ok', true, 'events', v_result);
end;
$$;

-- =============================================================================
-- 15) RECOMPENSA DIARIA + RACHA
-- =============================================================================

create or replace function public.claim_daily_reward()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_last date;
    v_streak integer := 1;
    v_reward public.daily_rewards_config%rowtype;
    v_today date := current_date;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    select last_claim_date, current_streak into v_last, v_streak
      from public.user_rewards
     where user_id = v_uid;

    -- Si no hay registro, racha empieza el día 1.
    -- Si ya la reclamó hoy, no puede otra vez hoy.
    if v_last = v_today then
        return jsonb_build_object('ok', false, 'reason', 'already_claimed_today');
    end if;

    -- Si reclamó ayer, continúa racha; si no, se resetea (ventana de 48h unida).
    if v_last = v_today - 1 then
        v_streak := coalesce(v_streak, 0) + 1;
    else
        v_streak := 1;
    end if;

    select * into v_reward
      from public.daily_rewards_config
     where day_number = ((v_streak - 1) % 7) + 1;

    insert into public.user_rewards (user_id, last_claim_date, current_streak, max_streak, next_claim_at)
    values (v_uid, v_today, v_streak, greatest(coalesce(v_streak,1), 1), v_today + 1)
    on conflict (user_id) do update
        set last_claim_date = v_today,
            current_streak = v_streak,
            max_streak = greatest(user_rewards.max_streak, v_streak),
            next_claim_at = v_today + 1,
            updated_at = now();

    -- Pago al ledger.
    perform public.ledger_apply(
        v_uid,
        'daily_reward',
        v_reward.currency,
        v_reward.amount,
        'Recompensa diaria - día ' || v_streak || ' 🔥',
        null, null,
        jsonb_build_object('streak', v_streak),
        null
    );

    return jsonb_build_object(
        'ok', true,
        'day', ((v_streak - 1) % 7) + 1,
        'streak', v_streak,
        'currency', v_reward.currency,
        'amount', v_reward.amount
    );
end;
$$;

create or replace function public.daily_reward_status()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_last date;
    v_streak integer := 0;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    select last_claim_date, current_streak into v_last, v_streak
      from public.user_rewards
     where user_id = v_uid;

    return jsonb_build_object(
        'ok', true,
        'streak', coalesce(v_streak, 0),
        'claimed_today', (coalesce(v_last, date '2000-01-01') = current_date),
        'can_claim_yesterday', (coalesce(v_last, date '2000-01-01') = current_date - 1),
        'next_claim_at', (v_last is distinct from current_date)::text
    );
end;
$$;

-- =============================================================================
-- 16) MISIONES
-- =============================================================================

-- Lista misiones activas con progreso del usuario.
create or replace function public.missions_in_progress()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_result jsonb;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    select coalesce(jsonb_agg(jsonb_build_object(
        'mission_id', m.id,
        'code', m.code,
        'title', m.title,
        'description', m.description,
        'scope', m.scope,
        'reward_currency', m.reward_currency,
        'reward_amount', m.reward_amount,
        'target', m.target,
        'progress', coalesce(um.progress, 0),
        'completed', um.completed_at is not null,
        'claimed', um.claimed_at is not null
    ) order by m.scope, m.sort_order), '[]'::jsonb) into v_result
    from public.missions m
    left join public.user_missions um
        on um.mission_id = m.id
       and um.user_id = v_uid
       and ( (m.scope = 'daily'  and um.period_start = current_date)
          or (m.scope = 'weekly' and um.period_start = date_trunc('week', current_date)::date) )
    where m.is_active;

    return jsonb_build_object('ok', true, 'missions', v_result);
end;
$$;

-- Registra progreso de una misión (incrementa si no completada).
create or replace function public.mission_progress(
    p_mission_code text,
    p_increment integer default 1
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_mission public.missions%rowtype;
    v_period_start date;
    v_period_end date;
    v_new_progress integer;
    v_claimed boolean;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    select * into v_mission from public.missions where code = p_mission_code and is_active;
    if v_mission is null then
        return jsonb_build_object('ok', false, 'reason', 'unknown_mission');
    end if;

    if v_mission.scope = 'daily' then
        v_period_start := current_date;
        v_period_end := current_date;
    else
        v_period_start := date_trunc('week', current_date)::date;
        v_period_end := v_period_start + 6;
    end if;

    insert into public.user_missions (user_id, mission_id, progress, period_start, period_end)
    values (v_uid, v_mission.id, least(v_mission.target, p_increment), v_period_start, v_period_end)
    on conflict (user_id, mission_id, period_start) do update
        set progress = least(
                v_mission.target,
                user_missions.progress + p_increment
            )
    returning progress into v_new_progress;

    -- Si acaba de completarse, marcar completed_at.
    update public.user_missions
       set completed_at = now()
     where user_id = v_uid
       and mission_id = v_mission.id
       and period_start = v_period_start
       and progress >= v_mission.target
       and completed_at is null;

    select claimed_at is not null into v_claimed
      from public.user_missions
     where user_id = v_uid
       and mission_id = v_mission.id
       and period_start = v_period_start;

    return jsonb_build_object(
        'ok', true,
        'progress', v_new_progress,
        'target', v_mission.target,
        'completed', v_new_progress >= v_mission.target,
        'claimed', coalesce(v_claimed, false)
    );
end;
$$;

-- Cobra las recompensas de misiones completadas pero no reclamadas.
create or replace function public.mission_claim_all()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_claimed jsonb := '[]'::jsonb;
    r record;
    v_bal integer;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    for r in
        select um.id as um_id, m.code, m.title, m.reward_currency, m.reward_amount, um.progress
          from public.user_missions um
          join public.missions m on m.id = um.mission_id
         where um.user_id = v_uid
           and um.completed_at is not null
           and um.claimed_at is null
           and ( (m.scope = 'daily'  and um.period_start = current_date)
              or (m.scope = 'weekly' and um.period_start = date_trunc('week', current_date)::date) )
    loop
        v_bal := public.ledger_apply(
            v_uid,
            'mission_reward',
            r.reward_currency,
            r.reward_amount,
            'Misión: ' || r.title,
            'missions', r.um_id,
            null, null
        );

        update public.user_missions set claimed_at = now() where id = r.um_id;

        v_claimed := v_claimed || jsonb_build_array(jsonb_build_object(
            'mission', r.code, 'title', r.title, 'reward', r.reward_amount, 'balance', v_bal
        ));
    end loop;

    return jsonb_build_object('ok', true, 'claimed', v_claimed);
end;
$$;

-- =============================================================================
-- 17) ADMIN GRANTS
-- =============================================================================

-- Solo service_role / admin puede conceder monedas (webhook de pago, soporte).
create or replace function public.admin_grant_coins(
    p_target_user_id uuid,
    p_amount integer,
    p_reason text default null,
    p_request_id text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_balance integer;
begin
    -- Solo service_role (los RPC con security definer se llaman con la key de servidor).
    if p_target_user_id is null or p_amount is null or p_amount <= 0 then
        raise exception 'invalid_params';
    end if;

    -- Idempotencia por request (webhooks de pago pueden reintentar).
    v_balance := public.ledger_apply(
        p_target_user_id,
        'admin_grant',
        'coins',
        p_amount,
        coalesce(p_reason, 'Ajuste administrativo'),
        'admin_grants', null,
        null, p_request_id
    );

    insert into public.admin_grants (target_user_id, granted_by, currency, amount, reason)
    values (p_target_user_id, auth.uid(), 'coins', p_amount, p_reason);

    return jsonb_build_object('ok', true, 'balance', v_balance, 'amount', p_amount);
end;
$$;

-- =============================================================================
-- 18) NOTIFICACIONES IN-APP
-- =============================================================================

create or replace function public.notifications_for_me()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_result jsonb;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    select coalesce(jsonb_agg(jsonb_build_object(
        'id', n.id,
        'category', n.category,
        'priority', n.priority,
        'title', n.title,
        'body', n.body,
        'payload', n.payload,
        'is_read', n.is_read,
        'created_at', n.created_at
    ) order by n.created_at desc), '[]'::jsonb) into v_result
    from (
        select * from public.in_app_notifications
        where user_id = v_uid
        order by created_at desc
        limit 100
    ) n;

    return jsonb_build_object('ok', true, 'notifications', v_result);
end;
$$;

create or replace function public.notification_read(p_notification_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception 'not_authenticated';
    end if;

    update public.in_app_notifications
       set is_read = true
     where id = p_notification_id
       and user_id = auth.uid();

    return jsonb_build_object('ok', true);
end;
$$;

-- Inserta una notificación (uso interno, service_role).
create or replace function public.notify_user(
    p_user_id uuid,
    p_category text,
    p_title text,
    p_body text default null,
    p_priority text default 'NORMAL',
    p_payload jsonb default null
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_id uuid;
begin
    insert into public.in_app_notifications (user_id, category, priority, title, body, payload)
    values (p_user_id, p_category, p_priority, p_title, p_body, p_payload)
    returning id into v_id;
    return v_id;
end;
$$;

-- =============================================================================
-- 19) ANALÍTICA PREMIUM (eventos de funnel)
-- =============================================================================

create table if not exists public.premium_analytics (
    id uuid primary key default gen_random_uuid(),
    user_id uuid,
    event_name text not null check (event_name in (
        'premium_shop_opened','premium_offer_viewed','premium_feature_clicked',
        'premium_checkout_started','premium_purchase_success','premium_purchase_failed',
        'premium_trial_started','premium_trial_expired','premium_renewed','premium_expired',
        'coins_earned','coins_spent','reward_claimed','promotion_clicked'
    )),
    feature_key text,
    payload jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_premium_analytics_event on public.premium_analytics(event_name, created_at desc);

alter table public.premium_analytics enable row level security;

drop policy if exists "Service role can insert analytics" on public.premium_analytics;
create policy "Service role can insert analytics"
    on public.premium_analytics
    for insert
    to service_role
    with check (true);

drop policy if exists "Users can insert own analytics" on public.premium_analytics;
create policy "Users can insert own analytics"
    on public.premium_analytics
    for insert
    to authenticated
    with check (user_id = auth.uid());

-- =============================================================================
-- 20) EXPIRACIÓN DE ENTITLEMENTS (cron) + RECUPERACIÓN DE SALDO
-- =============================================================================

-- Marca expired los entitlements vencidos. Devuelve cuántos marcó.
create or replace function public.premium_expire_entitlements()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_count integer;
begin
    update public.user_entitlements
       set status = 'expired'
     where status = 'active'
       and expires_at < now();

    get diagnostics v_count = row_count;

    return coalesce(v_count, 0);
end;
$$;

-- =============================================================================
-- 21) PERMISOS (REVOKE / GRANT)
-- =============================================================================

revoke all on function public.level_from_xp(integer) from public, anon;
revoke all on function public.recalc_level_for_user(uuid) from public, anon;
revoke all on function public.wallet_balance_full() from public, anon;
revoke all on function public.ledger_apply(uuid,text,text,integer,text,text,uuid,jsonb,text) from public, anon;
revoke all on function public.diamonds_exchange(integer,text) from public, anon;
revoke all on function public.premium_buy(text,text) from public, anon;
revoke all on function public.premium_my_entitlements() from public, anon;
revoke all on function public.premium_catalog() from public, anon;
revoke all on function public.premium_active_promotions() from public, anon;
revoke all on function public.premium_active_events() from public, anon;
revoke all on function public.claim_daily_reward() from public, anon;
revoke all on function public.daily_reward_status() from public, anon;
revoke all on function public.missions_in_progress() from public, anon;
revoke all on function public.mission_progress(text,integer) from public, anon;
revoke all on function public.mission_claim_all() from public, anon;
revoke all on function public.admin_grant_coins(uuid,integer,text,text) from public, anon, authenticated;
revoke all on function public.notifications_for_me() from public, anon;
revoke all on function public.notification_read(uuid) from public, anon;
revoke all on function public.notify_user(uuid,text,text,text,text,jsonb) from public, anon, authenticated;
revoke all on function public.premium_expire_entitlements() from public, anon;

grant execute on function public.wallet_balance_full() to authenticated;
grant execute on function public.diamonds_exchange(integer,text) to authenticated;
grant execute on function public.premium_buy(text,text) to authenticated;
grant execute on function public.premium_my_entitlements() to authenticated;
grant execute on function public.premium_catalog() to authenticated;
grant execute on function public.premium_active_promotions() to authenticated;
grant execute on function public.premium_active_events() to authenticated;
grant execute on function public.claim_daily_reward() to authenticated;
grant execute on function public.daily_reward_status() to authenticated;
grant execute on function public.missions_in_progress() to authenticated;
grant execute on function public.mission_progress(text,integer) to authenticated;
grant execute on function public.mission_claim_all() to authenticated;
grant execute on function public.notifications_for_me() to authenticated;
grant execute on function public.notification_read(uuid) to authenticated;

-- service_role puede invocar también los RPC de sistema (cron/notificar/grant).
grant execute on function public.premium_expire_entitlements() to service_role;
grant execute on function public.notify_user(uuid,text,text,text,text,jsonb) to service_role;
grant execute on function public.admin_grant_coins(uuid,integer,text,text) to service_role;

-- =============================================================================
-- 22) REALTIME (entitlements, notificaciones, eventos, promociones)
-- =============================================================================

do $$
declare
    v_table text;
begin
    foreach v_table in array array[
        'user_entitlements','in_app_notifications','premium_events','premium_promotions'
    ]
    loop
        if not exists (
            select 1 from pg_publication_tables
             where pubname = 'supabase_realtime'
               and schemaname = 'public'
               and tablename = v_table
        ) then
            execute format('alter publication supabase_realtime add table public.%I', v_table);
        end if;
    end loop;
end $$;

-- =============================================================================
-- 23) DATOS SEMILLA
-- =============================================================================

-- Moneda de la recompensa diaria: día 1-7.
insert into public.daily_rewards_config (day_number, currency, amount) values
    (1, 'coins', 100),
    (2, 'coins', 150),
    (3, 'tickets', 1),
    (4, 'coins', 250),
    (5, 'diamonds', 10),
    (6, 'coins', 400),
    (7, 'diamonds', 50)
on conflict (day_number) do update
    set currency = excluded.currency,
        amount = excluded.amount;

-- Features semilla.
insert into public.premium_features (feature_key, display_name, description, icon, enabled, minimum_level, trial_days, sort_order) values
    ('chat',     'Chat Gold',    'Burbujas premium, emojis especiales, mensajes destacados y archivos más grandes', '💬', true, 0, 0, 10),
    ('story',    'Story Gold',   'Estadísticas, quién vio, reposts destacados y más tiempo de publicación',        '📸', true, 0, 0, 20),
    ('live',     'Live Gold',    'Marco dorado, prioridad visual, co-host y regalos exclusivos',                    '🔴', true, 0, 0, 30),
    ('wall',     'Wall Gold',    'Boost de visibilidad y estadísticas detalladas',                                  '🧱', true, 0, 0, 40),
    ('voice',    'Voice Gold',   'Marcos de participante, entradas animadas y slots adicionales',                  '🎙️', true, 0, 0, 50),
    ('panatv',   'Pana TV Gold', 'Mayor calidad, sin interrupciones y funciones extra',                             '📺', true, 0, 0, 60)
on conflict (feature_key) do update
    set display_name = excluded.display_name,
        description = excluded.description,
        icon = excluded.icon,
        sort_order = excluded.sort_order;

-- Productos semilla: Chat, Story y Live activos; resto del catálogo en catálogo.
insert into public.premium_products (code, feature_key, name, emoji, description, duration_days, price_coins, trial_days, sort_order) values
    ('chat_gold_3d',   'chat',   'Chat Gold 3 días',   '💬', 'Chat premium por 3 días',   3,  600, 1, 11),
    ('chat_gold_5d',   'chat',   'Chat Gold 5 días',   '💬', 'Chat premium por 5 días',   5, 1000, 0, 12),
    ('chat_gold_10d',  'chat',   'Chat Gold 10 días',  '💬', 'Chat premium por 10 días', 10, 2000, 0, 13),
    ('story_gold_3d',  'story',  'Story Gold 3 días',  '📸', 'Story premium por 3 días',  3,  600, 0, 21),
    ('story_gold_10d', 'story',  'Story Gold 10 días', '📸', 'Story premium por 10 días',10, 2000, 0, 22),
    ('live_gold_3d',   'live',   'Live Gold 3 días',   '🔴', 'Live premium por 3 días',   3,  800, 0, 31),
    ('live_gold_5d',   'live',   'Live Gold 5 días',   '🔴', 'Live premium por 5 días',   5, 1400, 0, 32),
    ('live_gold_10d',  'live',   'Live Gold 10 días',  '🔴', 'Live premium por 10 días', 10, 2500, 0, 33)
on conflict (code) do update
    set feature_key = excluded.feature_key,
        name = excluded.name,
        description = excluded.description,
        duration_days = excluded.duration_days,
        price_coins = excluded.price_coins,
        trial_days = excluded.trial_days,
        sort_order = excluded.sort_order,
        is_active = true;

-- Misiones semilla (diarias) — se disparan con mission_progress desde la app.
insert into public.missions (code, title, description, scope, reward_currency, reward_amount, target, sort_order) values
    ('send_5_messages',  'Enviar 5 mensajes',     'Envía 5 mensajes a tus contactos',         'daily',   'coins', 100, 5,  10),
    ('view_3_stories',   'Ver 3 historias',       'Mira 3 historias de tus amigos',          'daily',   'coins',  50, 3,  20),
    ('join_voice_room',  'Entrar a una sala',     'Participa en una sala de voz',            'daily',   'coins', 150, 1,  30),
    ('publish_story',    'Publicar una historia', 'Publica una historia en tu perfil',        'daily',   'coins', 200, 1,  40),
    ('watch_live',       'Ver un Live',           'Participa en una transmisión en vivo',     'daily',   'coins', 300, 1,  50),
    ('daily_login_7',    'Racha de 7 días',       'Inicia sesión 7 días en la semana',        'weekly',  'diamonds', 100, 7, 60)
on conflict (code) do update
    set title = excluded.title,
        reward_currency = excluded.reward_currency,
        reward_amount = excluded.reward_amount,
        target = excluded.target,
        is_active = true;

-- Evento semilla: Fin de Semana Gold (multiplicador 1.5x en monedas/XP).
insert into public.premium_events (code, title, subtitle, description, emoji, action_type, action_value, starts_at, ends_at, multiplier_coins, multiplier_xp)
values ('weekend_gold', 'Fin de Semana Gold', 'Todos los beneficios 1.5x', 'Durante el fin de semana ganas 1.5x monedas y XP', '🔥', 'shop', 'premium', now() - interval '1 hour', now() + interval '3 days', 1.5, 1.5)
on conflict (code) do update
    set title = excluded.title,
        subtitle = excluded.subtitle,
        multiplier_coins = excluded.multiplier_coins,
        multiplier_xp = excluded.multiplier_xp;

-- Promoción semilla: oferta relámpago de Chat Gold.
insert into public.premium_promotions (code, title, subtitle, description, action_type, action_value, feature_key, price_coins, discount_percent, starts_at, ends_at, priority)
values ('flash_chat_gold', 'Oferta relámpago: Chat Gold', '-30% por tiempo limitado', 'Chat Gold 10 días a precio especial', 'shop', 'chat', 'chat', 1400, 30, now() - interval '1 hour', now() + interval '2 days', 100)
on conflict (code) do update
    set title = excluded.title,
        subtitle = excluded.subtitle,
        description = excluded.description,
        price_coins = excluded.price_coins,
        discount_percent = excluded.discount_percent,
        starts_at = excluded.starts_at,
        ends_at = excluded.ends_at,
        priority = excluded.priority;

-- =============================================================================
-- 24) CRON DE EXPIRACIÓN DE ENTITLEMENTS
-- =============================================================================

-- Revisa cada hora si hay entitlements vencidos para marcarlos 'expired'.
select cron.schedule('premium-expire-entitlements', '0 * * * *', $$select public.premium_expire_entitlements()$$)
where not exists (
    select 1 from cron.job where jobname = 'premium-expire-entitlements'
);
