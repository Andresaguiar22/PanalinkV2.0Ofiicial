-- =============================================================================
-- Premium 2.0 — Fase 5 (CERTIFICACIÓN): FIX DE CONCURRENCIA EN REWARDS
--
-- Hallazgo de auditoría: claim_daily_reward y mission_claim_all NO tenían
-- advisory lock por usuario (solo premium_buy y diamonds_exchange lo tenían,
-- ver 20260921030000). Dos claims simultáneos del mismo usuario podían pasar
-- el chequeo de "ya reclamado hoy" / "completed_at is not null" a la vez y
-- pagar la recompensa dos veces.
--
-- Fix: mismo patrón ya probado — pg_advisory_xact_lock(hashtextextended(...))
-- serializa todas las operaciones económicas del mismo usuario.
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

    -- SERIALIZA claims del mismo usuario (anti doble recompensa diaria).
    perform pg_advisory_xact_lock(hashtextextended(v_uid::text, 0));

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

    -- SERIALIZA claims de misiones del mismo usuario (anti doble recompensa).
    perform pg_advisory_xact_lock(hashtextextended(v_uid::text, 0));

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

    -- SERIALIZA progreso del mismo usuario (anti incrementos perdidos).
    perform pg_advisory_xact_lock(hashtextextended(v_uid::text, 0));

    select * into v_mission
      from public.missions
     where code = p_mission_code and is_active;

    if v_mission is null then
        return jsonb_build_object('ok', false, 'reason', 'unknown_mission');
    end if;

    if p_increment <= 0 then
        return jsonb_build_object('ok', false, 'reason', 'invalid_increment');
    end if;

    if v_mission.scope = 'weekly' then
        v_period_start := date_trunc('week', current_date)::date;
        v_period_end  := v_period_start + 7;
    else
        v_period_start := current_date;
        v_period_end  := current_date + 1;
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

revoke all on function public.claim_daily_reward() from public, anon;
revoke all on function public.mission_claim_all() from public, anon;
revoke all on function public.mission_progress(text, integer) from public, anon;
grant execute on function public.claim_daily_reward() to authenticated;
grant execute on function public.mission_claim_all() to authenticated;
grant execute on function public.mission_progress(text, integer) to authenticated;