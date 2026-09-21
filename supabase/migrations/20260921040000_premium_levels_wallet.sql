-- =============================================================================
-- Premium 2.0 — Fase 4.3 + 4.4: WALLET SCREEN (historial) + NIVELES
--
-- 4.4) premium_level_tiers: define títulos de nivel, recompensa automática al
--      alcanzar cada nivel y cosmético desbloqueado. El saldo XP->level ya lo
--      calcula level_from_xp(); esta tabla SOLO añade metadatos/recompensas.
--
-- 4.3) wallet_history(): historial unificado del usuario (entradas/salidas,
--      origen, currency, balance_after) para el WalletScreen.
--
-- Al subir de nivel se dispara una notificación y, si el tier define recompensa,
-- se acredita automáticamente (una sola vez vía premium_level_claims).
-- =============================================================================

create table if not exists public.premium_level_tiers (
    level integer primary key check (level >= 1),
    title text not null,
    emoji text not null default '⭐',
    reward_currency text check (reward_currency in ('coins','diamonds','tickets','xp')) default 'coins',
    reward_amount integer not null default 0,
    cosmetic_code text,
    sort_order integer not null default 0
);

alter table public.premium_level_tiers enable row level security;
drop policy if exists "Anyone can read level tiers" on public.premium_level_tiers;
create policy "Anyone can read level tiers"
    on public.premium_level_tiers
    for select
    to authenticated
    using (true);

-- Recompensas reclamadas por subida de nivel (una sola vez por nivel).
create table if not exists public.premium_level_claims (
    user_id uuid not null references auth.users(id) on delete cascade,
    level integer not null,
    claimed_at timestamptz not null default now(),
    reward_currency text,
    reward_amount integer not null default 0,
    primary key (user_id, level)
);
alter table public.premium_level_claims enable row level security;
drop policy if exists "Users can select own level claims" on public.premium_level_claims;
create policy "Users can select own level claims"
    on public.premium_level_claims
    for select
    to authenticated
    using (user_id = auth.uid());

-- Seed de niveles (6 rangos).
insert into public.premium_level_tiers (level, title, emoji, reward_currency, reward_amount, cosmetic_code, sort_order) values
    (1,  'Pana Nuevo',  '🌱', 'coins',      0,   null,            10),
    (5,  'Pana Activo', '🔥', 'coins',    100,   null,            20),
    (10, 'Pana',        '⭐', 'coins',    500,   'frame_gold',    30),
    (20, 'Pana Gold',   '👑', 'diamonds',   10,   'pendant_crown', 40),
    (30, 'Pana Elite',  '💎', 'diamonds',   30,   'frame_diamond', 50),
    (50, 'Pana Legend', '🏆', 'tickets',     1,   'frame_legend',  60)
on conflict (level) do update
    set title = excluded.title,
        emoji = excluded.emoji,
        reward_currency = excluded.reward_currency,
        reward_amount = excluded.reward_amount,
        cosmetic_code = excluded.cosmetic_code,
        sort_order = excluded.sort_order;

-- =============================================================================
-- RPC: historial de wallet del usuario autenticado.
-- =============================================================================
create or replace function public.wallet_history(
    p_limit integer default 50,
    p_currency text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_rows jsonb;
    v_total_coins integer;
    v_total_diamonds integer;
    v_total_tickets integer;
    v_total_xp integer;
    v_level integer;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    select coins, diamonds, tickets, xp, level into v_total_coins, v_total_diamonds, v_total_tickets, v_total_xp, v_level
      from public.user_wallets where user_id = v_uid;

    select coalesce(jsonb_agg(row_to_json(t)), '[]'::jsonb) into v_rows
    from (
        select id, kind, currency, amount, balance_after, description, ref_type, ref_id, meta, created_at
        from public.wallet_transactions
        where user_id = v_uid
          and (p_currency is null or currency = p_currency)
        order by created_at desc
        limit greatest(1, least(p_limit, 200))
    ) t;

    return jsonb_build_object(
        'ok', true,
        'wallet', jsonb_build_object(
            'coins', v_total_coins,
            'diamonds', v_total_diamonds,
            'tickets', v_total_tickets,
            'xp', v_total_xp,
            'level', v_level
        ),
        'transactions', v_rows,
        'count', jsonb_array_length(v_rows)
    );
end;
$$;

revoke all on function public.wallet_history(integer, text) from public, anon;
grant execute on function public.wallet_history(integer, text) to authenticated;

-- =============================================================================
-- RPC: información de nivel (actual + próximas recompensas).
-- =============================================================================
create or replace function public.premium_level_info()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_xp integer;
    v_level integer;
    v_prev_note text;
    v_current jsonb;
    v_next jsonb;
    v_xp_for_next integer;
    v_xp_prev integer;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    select coalesce(xp, 0), coalesce(level, 1) into v_xp, v_level from public.user_wallets where user_id = v_uid;

    select jsonb_build_object('level', t.level, 'title', t.title, 'emoji', t.emoji,
                              'reward_currency', t.reward_currency, 'reward_amount', t.reward_amount,
                              'cosmetic_code', t.cosmetic_code)
      into v_current
      from public.premium_level_tiers t
     where t.level <= v_level
     order by t.level desc
     limit 1;

    select jsonb_build_object('level', t.level, 'title', t.title, 'emoji', t.emoji,
                              'reward_currency', t.reward_currency, 'reward_amount', t.reward_amount,
                              'cosmetic_code', t.cosmetic_code)
      into v_next
      from public.premium_level_tiers t
     where t.level > v_level
     order by t.level asc
     limit 1;

    -- XP requerido para el próximo hit (mín 100 xp por nivel).
    v_xp_prev := v_level * 50;
    v_xp_for_next := greatest((v_next->>'level')::int * 50, v_xp_prev + 100);

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
                from public.premium_level_tiers order by level
            ) t
        )
    );
end;
$$;

revoke all on function public.premium_level_info() from public, anon;
grant execute on function public.premium_level_info() to authenticated;

-- =============================================================================
-- Trigger: al subir de nivel, notificar + acreditar recompensa del tier UNA vez.
-- =============================================================================
create or replace function public.trg_premium_level_up()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_tier public.premium_level_tiers%rowtype;
    v_reward_currency text;
    v_reward_amount integer;
    v_balance_after integer;
begin
    if coalesce(new.level, 1) <= coalesce(old.level, 1) then
        return new;
    end if;

    -- Recompensa del tier (si existe y aún no reclamada).
    select * into v_tier from public.premium_level_tiers where level = new.level;
    if v_tier is not null and v_tier.reward_amount > 0 and not exists (
        select 1 from public.premium_level_claims where user_id = new.user_id and level = new.level
    ) then
        v_reward_currency := coalesce(v_tier.reward_currency, 'coins');
        v_reward_amount := v_tier.reward_amount;

        -- Acredita vía ledger_apply (lanza ajuste idempotente).
        v_balance_after := public.ledger_apply(
            new.user_id,
            case when v_reward_currency = 'coins' then 'event_reward' else 'reward' end,
            v_reward_currency,
            v_reward_amount,
            'Recompensa de nivel ' || new.level || ' (' || v_tier.title || ')',
            'premium_level_tiers', null,
            null, 'lvl-' || new.user_id::text || '-' || new.level
        );

        insert into public.premium_level_claims (user_id, level, reward_currency, reward_amount)
        values (new.user_id, new.level, v_reward_currency, v_reward_amount)
        on conflict do nothing;

        insert into public.premium_audit_log (actor_user_id, target_user_id, action, request_id, metadata)
        values (new.user_id, new.user_id, 'reward',
                'lvl-' || new.user_id::text || '-' || new.level,
                jsonb_build_object('level', new.level, 'title', v_tier.title,
                                   'currency', v_reward_currency, 'amount', v_reward_amount,
                                   'balance_after', v_balance_after));
    end if;

    -- Notificación de subida de nivel.
    perform public.notify_user(
        new.user_id, 'REWARD',
        '¡Subiste al nivel ' || new.level || '! ' || coalesce(v_tier.emoji, '⭐'),
        case when v_tier is not null and v_tier.reward_amount > 0
             then 'Recibiste ' || v_tier.reward_amount || ' como recompensa.'
             else 'Seguí sumando XP para desbloquear más recompensas.' end,
        'HIGH',
        jsonb_build_object('level', new.level)
    );

    return new;
end;
$$;

drop trigger if exists trg_premium_level_up on public.user_wallets;
create trigger trg_premium_level_up
after update on public.user_wallets
for each row execute function public.trg_premium_level_up();