-- =============================================================================
-- Premium 2.0 — Fase 4.7 (UI): my_cosmetics devuelve también el cosmético
-- EQUIPADO (perfil.pendant_code) para que la galería sepa qué pintar.
-- =============================================================================

create or replace function public.my_cosmetics()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_level integer;
    v_owned jsonb;
    v_locked jsonb;
    v_equipped text;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    select coalesce(level, 1) into v_level from public.user_wallets where user_id = v_uid;

    select pendant_code into v_equipped from public.profiles where id = v_uid;

    select coalesce(jsonb_agg(row_to_json(t)), '[]'::jsonb) into v_owned
    from (
        select c.cosmetic_code, c.source_level, c.acquired_at
        from public.user_cosmetics c
        where c.user_id = v_uid
    ) t;

    -- Cosméticos que el tier de nivel otorga y el usuario aún no posee.
    select coalesce(jsonb_agg(row_to_json(t)), '[]'::jsonb) into v_locked
    from (
        select l.level, l.cosmetic_code
        from public.premium_level_tiers l
        where l.cosmetic_code is not null
          and l.level <= v_level
          and l.cosmetic_code not in (select cosmetic_code from public.user_cosmetics where user_id = v_uid)
        order by l.level
    ) t;

    return jsonb_build_object(
        'ok', true,
        'level', v_level,
        'equipped', v_equipped,
        'owned', v_owned,
        'upgradable_now', v_locked
    );
end;
$$;
revoke all on function public.my_cosmetics() from public, anon;
grant execute on function public.my_cosmetics() to authenticated;