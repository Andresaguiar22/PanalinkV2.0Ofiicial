-- =============================================================================
-- Premium 2.0 — Fase 4.7: COSMÉTICOS (marcos de avatar desbloqueables)
--
-- 1) Corrige premium_level_tiers: cosmetic_code usa códigos REALES del
--    AvatarFrameCatalog de la app (gold, diamond, crown, halo...).
-- 2) user_cosmetics: cosméticos que el usuario HA DESBLOQUEADO (por nivel,
--    por compra, por evento). Un cosmético requiere el level mínimo y una vez
--    desbloqueado queda "equipable" en el perfil.
-- 3) equip_cosmetic(): el usuario equipa un cosmético desbloqueado (guarda
--    en profiles.pendant_code, igual que el colgante de sala de voz).
-- =============================================================================

-- 1) Corregir códigos del seed de niveles (de frame_* inexistentes a reales).
update public.premium_level_tiers
   set cosmetic_code = 'gold'
 where cosmetic_code = 'frame_gold';
update public.premium_level_tiers
   set cosmetic_code = 'diamond'
 where cosmetic_code = 'frame_diamond';
update public.premium_level_tiers
   set cosmetic_code = 'legend'
 where cosmetic_code = 'frame_legend'; -- temporal: no existe en catálogo; se resuelve abajo
update public.premium_level_tiers
   set cosmetic_code = 'crown'
 where cosmetic_code = 'pendant_crown';

-- 'legend' no existe en AvatarFrameCatalog -> usar 'galaxy' (nivel 50+ = espectacular).
update public.premium_level_tiers
   set cosmetic_code = 'galaxy'
 where cosmetic_code = 'legend';

-- 2) Tabla de cosméticos desbloqueados (ownership).
create table if not exists public.user_cosmetics (
    user_id uuid not null references auth.users(id) on delete cascade,
    cosmetic_code text not null,
    source_level integer,
    source_free boolean not null default false,
    acquired_at timestamptz not null default now(),
    primary key (user_id, cosmetic_code)
);
alter table public.user_cosmetics enable row level security;
drop policy if exists "Users can select own cosmetics" on public.user_cosmetics;
create policy "Users can select own cosmetics"
    on public.user_cosmetics
    for select
    to authenticated
    using (user_id = auth.uid());
drop policy if exists "Users can insert own cosmetics" on public.user_cosmetics;
create policy "Users can insert own cosmetics"
    on public.user_cosmetics
    for insert
    to authenticated
    with check (user_id = auth.uid());

-- 3) RPC: lista cosméticos propios (+ desbloqueables por nivel).
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
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    select coalesce(level, 1) into v_level from public.user_wallets where user_id = v_uid;

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
        'owned', v_owned,
        'upgradable_now', v_locked
    );
end;
$$;
revoke all on function public.my_cosmetics() from public, anon;
grant execute on function public.my_cosmetics() to authenticated;

-- RPC: equipar un cosmético en el perfil (escribe profiles.pendant_code).
create or replace function public.equip_cosmetic(p_cosmetic_code text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_owned boolean;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;
    if p_cosmetic_code is null or p_cosmetic_code = '' then
        raise exception 'invalid_code';
    end if;

    -- 'none' = des-equipar (deja el avatar sin marco). No requiere poseerlo.
    if p_cosmetic_code = 'none' then
        update public.profiles
           set pendant_code = 'none'
         where id = v_uid;
        return jsonb_build_object('ok', true, 'equipped', 'none');
    end if;

    -- Debe poseerlo (desbloqueado por nivel u otorgado).
    select exists(
        select 1 from public.user_cosmetics c where c.user_id = v_uid and c.cosmetic_code = p_cosmetic_code
    ) into v_owned;

    if not v_owned then
        -- Auto-otorga si se alcanzó el nivel del tier (por ejemplo senior).
        if exists (
            select 1 from public.premium_level_tiers l
            join public.user_wallets w on w.user_id = v_uid
            where l.cosmetic_code = p_cosmetic_code and l.level <= w.level
        ) then
            insert into public.user_cosmetics (user_id, cosmetic_code, source_level)
            select v_uid, l.cosmetic_code, l.level
            from public.premium_level_tiers l
            join public.user_wallets w on w.user_id = v_uid
            where l.cosmetic_code = p_cosmetic_code and l.level <= w.level
            on conflict do nothing;
            v_owned := true;
        end if;
    end if;

    if not v_owned then
        return jsonb_build_object('ok', false, 'reason', 'not_owned');
    end if;

    -- Equipa en el perfil (mismo campo que el colgante de sala de voz).
    update public.profiles
       set pendant_code = p_cosmetic_code
     where id = v_uid;

    return jsonb_build_object('ok', true, 'equipped', p_cosmetic_code);
end;
$$;
revoke all on function public.equip_cosmetic(text) from public, anon;
grant execute on function public.equip_cosmetic(text) to authenticated;

-- =============================================================================
-- Trigger: al subir de nivel, DESBLOQUEA automáticamente el cosmético del tier.
-- (Antes del trigger de recompensa; los levels son secuenciales.)
-- =============================================================================
create or replace function public.trg_premium_level_cosmetic_unlock()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    r record;
    v_min_lvl integer := coalesce(old.level, 1);
    v_max_lvl integer := coalesce(new.level, 1);
begin
    if v_max_lvl <= v_min_lvl then return new; end if;

    for r in
        select t.cosmetic_code, t.level
        from public.premium_level_tiers t
        where t.cosmetic_code is not null
          and t.level > v_min_lvl and t.level <= v_max_lvl
    loop
        insert into public.user_cosmetics (user_id, cosmetic_code, source_level)
        values (new.user_id, r.cosmetic_code, r.level)
        on conflict (user_id, cosmetic_code) do nothing;
    end loop;

    return new;
end;
$$;

drop trigger if exists trg_premium_level_cosmetic_unlock on public.user_wallets;
create trigger trg_premium_level_cosmetic_unlock
after update on public.user_wallets
for each row execute function public.trg_premium_level_cosmetic_unlock();