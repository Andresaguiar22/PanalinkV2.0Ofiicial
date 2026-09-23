-- =============================================================================
-- Premium 2.0 — Fase 4.4 (fix): trigger de subida de nivel CON atravesado
--
-- Problema: el trigger AFTER UPDATE solo ve el nivel FINAL. Si un usuario
-- salta de nivel 1 a nivel 13 de una vez (mucho XP), la recompensa del tier 10
-- (Pana +500🪙) se perdía porque nunca se evaluó.
--
-- Fix: iterar TODOS los tiers entre old.level+1 y new.level y acreditar cada
-- uno que no haya sido reclamado (idempotente vía premium_level_claims).
-- =============================================================================

create or replace function public.trg_premium_level_up()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_tier public.premium_level_tiers%rowtype;
    v_min_lvl integer := coalesce(old.level, 1);
    v_max_lvl integer := coalesce(new.level, 1);
    r record;
begin
    if v_max_lvl <= v_min_lvl then
        return new;
    end if;

    -- Todos los tiers atravesados (niveles intermedios inclusos).
    for r in
        select t.level, t.title, t.emoji, t.reward_currency, t.reward_amount
        from public.premium_level_tiers t
        where t.level > v_min_lvl and t.level <= v_max_lvl
        order by t.level
    loop
        if r.reward_amount > 0 and not exists (
            select 1 from public.premium_level_claims
            where user_id = new.user_id and level = r.level
        ) then
            perform public.ledger_apply(
                new.user_id,
                case when coalesce(r.reward_currency, 'coins') = 'coins' then 'event_reward' else 'reward' end,
                coalesce(r.reward_currency, 'coins'),
                r.reward_amount,
                'Recompensa de nivel ' || r.level || ' (' || r.title || ')',
                'premium_level_tiers', null,
                null, 'lvl-' || new.user_id::text || '-' || r.level
            );

            insert into public.premium_level_claims (user_id, level, reward_currency, reward_amount)
            values (new.user_id, r.level, coalesce(r.reward_currency, 'coins'), r.reward_amount)
            on conflict do nothing;

            insert into public.premium_audit_log (actor_user_id, target_user_id, action, request_id, metadata)
            values (new.user_id, new.user_id, 'reward',
                    'lvl-' || new.user_id::text || '-' || r.level,
                    jsonb_build_object('level', r.level, 'title', r.title,
                                       'currency', r.reward_currency, 'amount', r.reward_amount));
        end if;
    end loop;

    -- Notificación única del nivel alcanzado.
    select title, emoji into r.title, r.emoji
      from public.premium_level_tiers t
     where t.level <= v_max_lvl
     order by t.level desc
     limit 1;

    perform public.notify_user(
        new.user_id, 'REWARD',
        '¡Subiste al nivel ' || v_max_lvl || '! ' || coalesce(r.emoji, '⭐'),
        'Seguí sumando XP para desbloquear más recompensas.',
        'HIGH',
        jsonb_build_object('level', v_max_lvl)
    );

    return new;
end;
$$;