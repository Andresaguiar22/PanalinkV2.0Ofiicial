-- =============================================================================
-- Premium 2.0 — Consistencia de economía
-- NO APLICAR A PRODUCCIÓN HASTA VALIDAR EN BETA.
--
-- Corrige:
-- 1) promociones reales en premium_buy + catálogo dinámico;
-- 2) mínimo de nivel de la feature;
-- 3) multiplicadores activos de eventos para recompensas;
-- 4) umbrales XP consistentes entre backend y UI.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) LEDGER: aplica multiplicadores activos de eventos solo a ganancias
-- económicas/recompensas, nunca a compras, débitos ni grants administrativos.
-- -----------------------------------------------------------------------------
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
    v_amount integer := p_amount;
    v_multiplier numeric := 1.0;
begin
    if p_request_id is not null then
        if exists (
            select 1 from public.wallet_transactions
             where request_id = p_request_id
        ) then
            select balance_after into v_new_balance
              from public.wallet_transactions
             where request_id = p_request_id
             order by created_at desc limit 1;
            return v_new_balance;
        end if;
    end if;

    -- Los eventos solo multiplican recompensas/ganancias positivas.
    if p_amount > 0
       and p_currency in ('coins', 'xp')
       and p_kind in (
           'daily_reward','mission_reward','event_reward',
           'reward_claim','reward','streak_reward'
       ) then
        if p_currency = 'coins' then
            select greatest(coalesce(max(multiplier_coins), 1.0), 1.0)
              into v_multiplier
              from public.premium_events
             where starts_at <= now()
               and ends_at > now()
               and multiplier_coins > 1.0;
        else
            select greatest(coalesce(max(multiplier_xp), 1.0), 1.0)
              into v_multiplier
              from public.premium_events
             where starts_at <= now()
               and ends_at > now()
               and multiplier_xp > 1.0;
        end if;

        v_amount := round(p_amount * v_multiplier)::integer;
    end if;

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

    v_new_balance := v_balance + v_amount;

    update public.user_wallets
       set coins = case when p_currency = 'coins' then v_new_balance else coins end,
           diamonds = case when p_currency = 'diamonds' then v_new_balance else diamonds end,
           tickets = case when p_currency = 'tickets' then v_new_balance else tickets end,
           xp = case when p_currency = 'xp' then v_new_balance else xp end,
           updated_at = now()
     where user_id = p_user_id;

    insert into public.wallet_transactions (
        user_id, kind, currency, amount, balance_after,
        description, ref_type, ref_id, meta, request_id
    ) values (
        p_user_id, p_kind, p_currency, v_amount, v_new_balance,
        p_description, p_ref_type, p_ref_id,
        coalesce(p_meta, '{}'::jsonb) ||
            jsonb_build_object(
                'base_amount', p_amount,
                'event_multiplier', v_multiplier
            ),
        p_request_id
    );

    if p_currency = 'xp' then
        perform public.recalc_level_for_user(p_user_id);
    end if;

    return v_new_balance;
end;
$$;

-- -----------------------------------------------------------------------------
-- 2) CATÁLOGO: expone precio original y precio promocional vigente.
-- -----------------------------------------------------------------------------
create or replace function public.premium_catalog()
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

    select coalesce(
        jsonb_agg(
            jsonb_build_object(
                'code', p.code,
                'feature_key', p.feature_key,
                'name', p.name,
                'emoji', p.emoji,
                'description', p.description,
                'duration_days', p.duration_days,
                'price_coins', coalesce(pr.price_coins, p.price_coins),
                'original_price_coins', p.price_coins,
                'promo_discount_percent', coalesce(pr.discount_percent, 0),
                'trial_days', p.trial_days,
                'sort_order', p.sort_order,
                'feature_enabled', f.enabled
            )
            order by p.sort_order, p.price_coins
        ),
        '[]'::jsonb
    )
    into v_result
    from public.premium_products p
    join public.premium_features f
      on f.feature_key = p.feature_key
    left join lateral (
        select pp.price_coins, pp.discount_percent
          from public.premium_promotions pp
         where pp.enabled
           and pp.starts_at <= now()
           and pp.ends_at > now()
           and pp.action_type = 'shop'
           and pp.feature_key = p.feature_key
           and pp.price_coins is not null
         order by pp.priority desc, pp.id
         limit 1
    ) pr on true
    where p.is_active;

    return jsonb_build_object('ok', true, 'products', v_result);
end;
$$;

-- -----------------------------------------------------------------------------
-- 3) COMPRA: mismo precio promocional que ve el catálogo + mínimo de nivel.
-- -----------------------------------------------------------------------------
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
    v_original_price integer;
    v_discount integer := 0;
    v_balance integer;
    v_new_expiry timestamptz;
    v_existing timestamptz;
    v_feature_flag boolean;
    v_minimum_level integer := 0;
    v_user_level integer := 1;
    v_ent_id uuid;
    v_stacked boolean := false;
    v_promo_id uuid;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    perform pg_advisory_xact_lock(hashtextextended(v_uid::text, 0));

    if p_product_code is null or p_product_code = '' then
        raise exception 'invalid_product';
    end if;

    if exists (
        select 1 from public.wallet_transactions
         where request_id = p_request_id and user_id = v_uid
    ) then
        return jsonb_build_object('ok', false, 'reason', 'duplicate_request');
    end if;

    select * into v_product
      from public.premium_products
     where code = p_product_code and is_active;

    if v_product is null then
        return jsonb_build_object('ok', false, 'reason', 'unknown_product');
    end if;

    select enabled, minimum_level
      into v_feature_flag, v_minimum_level
      from public.premium_features
     where feature_key = v_product.feature_key;

    if coalesce(v_feature_flag, false) is false then
        return jsonb_build_object('ok', false, 'reason', 'feature_disabled');
    end if;

    select coalesce(level, 1) into v_user_level
      from public.user_wallets
     where user_id = v_uid;

    if v_user_level < coalesce(v_minimum_level, 0) then
        return jsonb_build_object(
            'ok', false,
            'reason', 'minimum_level',
            'required_level', v_minimum_level,
            'current_level', v_user_level
        );
    end if;

    v_original_price := v_product.price_coins;
    v_price := v_original_price;

    select pp.id, pp.price_coins, pp.discount_percent
      into v_promo_id, v_price, v_discount
      from public.premium_promotions pp
     where pp.enabled
       and pp.starts_at <= now()
       and pp.ends_at > now()
       and pp.action_type = 'shop'
       and pp.feature_key = v_product.feature_key
       and pp.price_coins is not null
       and pp.price_coins >= 0
     order by pp.priority desc, pp.id
     limit 1;

    if v_promo_id is null then
        v_price := v_original_price;
        v_discount := 0;
    end if;

    -- Nunca permitir que una promoción incremente el precio del catálogo.
    v_price := least(v_price, v_original_price);

    update public.user_wallets
       set coins = coins - v_price,
           updated_at = now()
     where user_id = v_uid
       and coins >= v_price
    returning coins into v_balance;

    if v_balance is null then
        select coins into v_balance
          from public.user_wallets
         where user_id = v_uid;
        return jsonb_build_object(
            'ok', false,
            'reason', 'insufficient_funds',
            'balance', coalesce(v_balance, 0),
            'price_coins', v_price
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
    v_new_expiry := coalesce(v_existing, now()) +
        (v_product.duration_days || ' days')::interval;

    insert into public.user_entitlements (
        user_id, product_code, feature_key, starts_at, expires_at, status
    ) values (
        v_uid, p_product_code, v_product.feature_key, now(), v_new_expiry, 'active'
    )
    returning id into v_ent_id;

    insert into public.wallet_transactions (
        user_id, kind, currency, amount, balance_after, description,
        ref_type, ref_id, meta, request_id
    ) values (
        v_uid, 'premium_buy', 'coins', -v_price, v_balance,
        v_product.name, 'premium_products', v_product.id,
        jsonb_build_object(
            'product_code', p_product_code,
            'expires_at', v_new_expiry,
            'original_price_coins', v_original_price,
            'price_coins', v_price,
            'discount_percent', v_discount,
            'promotion_id', v_promo_id
        ),
        p_request_id
    );

    perform public.audit_log_write(
        v_uid, v_uid,
        case when v_stacked then 'renewal' else 'purchase' end,
        p_request_id,
        jsonb_build_object(
            'product_code', p_product_code,
            'feature_key', v_product.feature_key,
            'original_price_coins', v_original_price,
            'price_coins', v_price,
            'discount_percent', v_discount,
            'duration_days', v_product.duration_days,
            'expires_at', v_new_expiry,
            'entitlement_id', v_ent_id
        )
    );

    return jsonb_build_object(
        'ok', true,
        'balance', v_balance,
        'expires_at', v_new_expiry,
        'entitlement_id', v_ent_id,
        'feature_key', v_product.feature_key,
        'price_coins', v_price,
        'original_price_coins', v_original_price,
        'discount_percent', v_discount
    );
end;
$$;

-- -----------------------------------------------------------------------------
-- 4) XP: los umbrales de UI salen del mismo algoritmo que level_from_xp().
-- -----------------------------------------------------------------------------
create or replace function public.premium_level_info()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_xp integer;
    v_level integer;
    v_current jsonb;
    v_next jsonb;
    v_xp_for_next integer;
    v_xp_prev integer;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    select coalesce(xp, 0), coalesce(level, 1)
      into v_xp, v_level
      from public.user_wallets
     where user_id = v_uid;

    select jsonb_build_object(
        'level', t.level, 'title', t.title, 'emoji', t.emoji,
        'reward_currency', t.reward_currency,
        'reward_amount', t.reward_amount,
        'cosmetic_code', t.cosmetic_code
    )
    into v_current
    from public.premium_level_tiers t
    where t.level <= v_level
    order by t.level desc
    limit 1;

    select jsonb_build_object(
        'level', t.level, 'title', t.title, 'emoji', t.emoji,
        'reward_currency', t.reward_currency,
        'reward_amount', t.reward_amount,
        'cosmetic_code', t.cosmetic_code
    )
    into v_next
    from public.premium_level_tiers t
    where t.level > v_level
    order by t.level asc
    limit 1;

    -- Inversa exacta de level_from_xp():
    -- threshold(L) = 50 * (L*(L+1)/2 - 1), mínimo 0.
    v_xp_prev := greatest(0, 50 * (v_level * (v_level + 1) / 2 - 1));

    if v_next is null then
        v_xp_for_next := v_xp_prev;
    else
        v_xp_for_next := greatest(
            v_xp_prev + 1,
            50 * (
                (((v_next->>'level')::integer *
                  ((v_next->>'level')::integer + 1)) / 2) - 1
            )
        );
    end if;

    return jsonb_build_object(
        'ok', true,
        'level', v_level,
        'xp', v_xp,
        'xp_for_next', v_xp_for_next,
        'xp_for_prev', v_xp_prev,
        'current', v_current,
        'next', v_next,
        'level_tiers', (
            select coalesce(jsonb_agg(row_to_json(t)), '[]'::jsonb)
            from (
                select level, title, emoji, reward_currency, reward_amount, cosmetic_code
                from public.premium_level_tiers
                order by level
            ) t
        )
    );
end;
$$;

revoke all on function public.ledger_apply(uuid,text,text,integer,text,text,uuid,jsonb,text) from public, anon;
revoke all on function public.premium_catalog() from public, anon;
revoke all on function public.premium_buy(text,text) from public, anon;
revoke all on function public.premium_level_info() from public, anon;

grant execute on function public.premium_catalog() to authenticated;
grant execute on function public.premium_buy(text,text) to authenticated;
grant execute on function public.premium_level_info() to authenticated;
