-- =============================================================================
-- Premium 2.0 — Fase 4.1: AUDITORÍA ADMINISTRATIVA
--
-- premium_audit_log: registro inmutable de acciones administrativas y de
-- economía (compra, renovación, grant, crédito/débito, canje, recompensa,
-- misión, cambio de entitlement, admin_action).
--
-- Propósito: reconstruir exactamente qué pasó si un usuario reclama
-- "me quitaron 2.000 monedas" -> oro para soporte.
--
-- Solo service_role/roles internos escriben. SELECT libre a service_role.
-- =============================================================================

create table if not exists public.premium_audit_log (
    id uuid primary key default gen_random_uuid(),
    actor_user_id uuid references auth.users(id) on delete set null,
    target_user_id uuid references auth.users(id) on delete cascade,
    action text not null check (action in (
        'purchase', 'renewal', 'grant', 'coin_credit', 'coin_debit',
        'diamond_exchange', 'reward', 'mission', 'entitlement_change',
        'admin_action', 'notification_sent', 'rpc_rejected'
    )),
    request_id text,
    metadata jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_premium_audit_target on public.premium_audit_log(target_user_id, created_at desc);
create index if not exists idx_premium_audit_action on public.premium_audit_log(action, created_at desc);

alter table public.premium_audit_log enable row level security;

-- Solo service_role puede SELECT el log completo (el usuario no necesita ver
-- el log administrativo; su historial está en wallet_transactions).
drop policy if exists "Only service role can read audit log" on public.premium_audit_log;
create policy "Only service role can read audit log"
    on public.premium_audit_log
    for select
    to service_role
    using (true);

-- =============================================================================
-- Helper de auditoría (security definer, uso interno).
-- =============================================================================
create or replace function public.audit_log_write(
    p_actor uuid,
    p_target uuid,
    p_action text,
    p_request_id text default null,
    p_metadata jsonb default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.premium_audit_log (actor_user_id, target_user_id, action, request_id, metadata)
    values (p_actor, p_target, p_action, p_request_id, p_metadata);
end;
$$;

revoke all on function public.audit_log_write(uuid, uuid, text, text, jsonb) from public, anon, authenticated;
grant execute on function public.audit_log_write(uuid, uuid, text, text, jsonb) to service_role;

-- =============================================================================
-- Instrumentación: premium_buy (purchase/renewal + coin_debit)
-- =============================================================================
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

    -- ¿Renovación (stacking) o compra nueva?
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

    -- AUDITORÍA
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

-- =============================================================================
-- Instrumentación: diamonds_exchange
-- =============================================================================
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

    -- AUDITORÍA
    perform public.audit_log_write(
        v_uid, v_uid, 'diamond_exchange', p_request_id,
        jsonb_build_object('amount_diamonds', p_amount, 'rate', v_rate, 'coins_gained', p_amount * v_rate)
    );

    return jsonb_build_object('ok', true, 'coins', (select coins from public.user_wallets where user_id = v_uid));
end;
$$;

-- =============================================================================
-- Instrumentación: admin_grant_coins
-- =============================================================================
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
    if p_target_user_id is null or p_amount is null or p_amount <= 0 then
        raise exception 'invalid_params';
    end if;

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

    -- AUDITORÍA (actor = service_role/admin; target = usuario)
    perform public.audit_log_write(
        auth.uid(), p_target_user_id, 'grant', p_request_id,
        jsonb_build_object('amount', p_amount, 'reason', p_reason, 'balance_after', v_balance)
    );

    return jsonb_build_object('ok', true, 'balance', v_balance, 'amount', p_amount);
end;
$$;

-- =============================================================================
-- RPC de lectura del audit log (solo service_role)
-- =============================================================================
create or replace function public.premium_audit_query(
    p_target_user_id uuid default null,
    p_action text default null,
    p_limit integer default 50
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_rows jsonb;
begin
    select coalesce(jsonb_agg(row_to_json(t)), '[]'::jsonb)
    into v_rows
    from (
        select id, actor_user_id, target_user_id, action, request_id, metadata, created_at
        from public.premium_audit_log
        where (p_target_user_id is null or target_user_id = p_target_user_id)
          and (p_action is null or action = p_action)
        order by created_at desc
        limit greatest(1, least(p_limit, 500))
    ) t;

    return jsonb_build_object('ok', true, 'audit', v_rows);
end;
$$;

revoke all on function public.premium_audit_query(uuid, text, integer) from public, anon, authenticated;
grant execute on function public.premium_audit_query(uuid, text, integer) to service_role;