-- =============================================================================
-- Premium 2.0 — Fase 4.2: HARDENING DE CONCURRENCIA E IDEMPOTENCIA
--
-- 1. advisory lock por usuario en premium_buy -> serializa compras del mismo
--    usuario. Cierra: race del stacking (dos compras leen el mismo
--    expires_at), race del duplicate_request, y evita doble débito.
-- 2. Bloqueo NEW de count per minute para cada usuario en premium_buy y
--    diamonds_exchange (rate limit básico anti-bot).
--
-- Mantiene precios ASTM del catálogo server-side; jamás acepta price del
-- cliente. El débito sigue siendo atómico (coins >= v_price).
-- =============================================================================

-- Tabla de rate limit (intentos de compra/canje por minuto).
create table if not exists public.premium_rate_limits (
    user_id uuid not null references auth.users(id) on delete cascade,
    action text not null,
    bucket_minute timestamptz not null default now(),
    attempts integer not null default 1,
    primary key (user_id, action, bucket_minute)
);

alter table public.premium_rate_limits enable row level security;
drop policy if exists "Rate limit service only" on public.premium_rate_limits;
create policy "Rate limit service only"
    on public.premium_rate_limits
    for all
    to service_role
    using (true);

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
    v_stacked boolean := false;
    v_rate_attempts integer;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    -- SERIALIZA todas las operaciones económicas del mismo usuario.
    perform pg_advisory_xact_lock(hashtextextended(v_uid::text, 0));

    -- Rate limit: max 10 compras/minuto por usuario.
    update public.premium_rate_limits
       set attempts = attempts + 1,
           bucket_minute = now()
     where user_id = v_uid
       and action = 'buy'
       and bucket_minute >= date_trunc('minute', now())
    returning attempts into v_rate_attempts;

    if v_rate_attempts is null then
        insert into public.premium_rate_limits (user_id, action, bucket_minute, attempts)
        values (v_uid, 'buy', date_trunc('minute', now()), 1);
        v_rate_attempts := 1;
    end if;

    if v_rate_attempts > 10 then
        return jsonb_build_object('ok', false, 'reason', 'rate_limited');
    end if;

    if p_product_code is null or p_product_code = '' then
        raise exception 'invalid_product';
    end if;

    -- Idempotencia POR request_id — bajo el advisory lock es seguro.
    if exists (select 1 from public.wallet_transactions where request_id = p_request_id and user_id = v_uid) then
        return jsonb_build_object('ok', false, 'reason', 'duplicate_request');
    end if;

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

    -- Débito atómico desde el saldo real del usuario (nunca price del cliente).
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

    select expires_at into v_existing
      from public.user_entitlements
     where user_id = v_uid
       and feature_key = v_product.feature_key
       and status = 'active'
     order by expires_at desc
     limit 1;

    v_stacked := coalesce(v_existing, now()) > now();
    v_new_expiry := coalesce(v_existing, now()) + (v_product.duration_days || ' days')::interval;

    insert into public.user_entitlements (user_id, product_code, feature_key, starts_at, expires_at, status)
    values (v_uid, p_product_code, v_product.feature_key, now(), v_new_expiry, 'active')
    returning id into v_ent_id;

    insert into public.wallet_transactions (
        user_id, kind, currency, amount, balance_after, description,
        ref_type, ref_id, meta, request_id
    ) values (
        v_uid, 'premium_buy', 'coins', -v_price, v_balance,
        v_product.name, 'premium_products', v_product.id,
        jsonb_build_object('product_code', p_product_code, 'expires_at', v_new_expiry),
        p_request_id
    );

    perform public.audit_log_write(
        v_uid, v_uid,
        case when v_stacked then 'renewal' else 'purchase' end,
        p_request_id,
        jsonb_build_object(
            'product_code', p_product_code,
            'feature_key', v_product.feature_key,
            'price_coins', v_price,
            'duration_days', v_product.duration_days,
            'expires_at', v_new_expiry,
            'entitlement_id', v_ent_id
        )
    );
    perform public.audit_log_write(
        v_uid, v_uid, 'coin_debit', p_request_id,
        jsonb_build_object('product_code', p_product_code, 'amount', -v_price, 'balance_after', v_balance)
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

-- Rate limit también en diamonds_exchange (max 5 canjes por minuto).
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
    v_rate_attempts integer;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;
    if p_amount is null or p_amount <= 0 then
        raise exception 'invalid_amount';
    end if;

    perform pg_advisory_xact_lock(hashtextextended(v_uid::text, 0));

    update public.premium_rate_limits
       set attempts = attempts + 1,
           bucket_minute = now()
     where user_id = v_uid
       and action = 'exchange'
       and bucket_minute >= date_trunc('minute', now())
    returning attempts into v_rate_attempts;

    if v_rate_attempts is null then
        insert into public.premium_rate_limits (user_id, action, bucket_minute, attempts)
        values (v_uid, 'exchange', date_trunc('minute', now()), 1);
        v_rate_attempts := 1;
    end if;

    if v_rate_attempts > 5 then
        return jsonb_build_object('ok', false, 'reason', 'rate_limited');
    end if;

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

    perform public.audit_log_write(
        v_uid, v_uid, 'diamond_exchange', p_request_id,
        jsonb_build_object('amount_diamonds', p_amount, 'rate', v_rate, 'coins_gained', p_amount * v_rate)
    );

    return jsonb_build_object('ok', true, 'coins', (select coins from public.user_wallets where user_id = v_uid));
end;
$$;