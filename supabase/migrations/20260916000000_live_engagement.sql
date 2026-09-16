-- Live engagement real: likes persistidos, regalos con wallet, contadores agregados
-- y correcciones de RLS de moderación. Idempotente: se puede reaplicar sin romper nada.
--
-- Diseño: los contadores agregados viven en public.live_stream_stats (tabla aparte de
-- live_streams) para que un "me gusta" o un latido de espectadores NO emita eventos
-- Realtime sobre live_streams y no provoque recargas del feed en todos los clientes.

-- ---------------------------------------------------------------------------
-- 1) Tabla de contadores agregados por transmisión
-- ---------------------------------------------------------------------------
create table if not exists public.live_stream_stats (
    stream_id uuid primary key references public.live_streams(id) on delete cascade,
    like_count integer not null default 0,
    gift_count integer not null default 0,
    gift_coins bigint not null default 0,
    viewer_count integer not null default 0,
    updated_at timestamptz not null default now()
);

alter table public.live_stream_stats enable row level security;

drop policy if exists "Authenticated users can select live stream stats" on public.live_stream_stats;
create policy "Authenticated users can select live stream stats"
    on public.live_stream_stats
    for select
    to authenticated
    using (true);

-- ---------------------------------------------------------------------------
-- 2) Reacciones (me gusta) persistidas
-- ---------------------------------------------------------------------------
create table if not exists public.live_reactions (
    id uuid primary key default gen_random_uuid(),
    stream_id uuid not null references public.live_streams(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    quantity integer not null default 1 check (quantity > 0),
    created_at timestamptz not null default now()
);

create index if not exists idx_live_reactions_stream on public.live_reactions(stream_id, created_at desc);
create index if not exists idx_live_reactions_user on public.live_reactions(user_id);

alter table public.live_reactions enable row level security;

drop policy if exists "Authenticated users can select live reactions" on public.live_reactions;
create policy "Authenticated users can select live reactions"
    on public.live_reactions
    for select
    to authenticated
    using (true);

-- ---------------------------------------------------------------------------
-- 3) Catálogo de regalos y eventos de regalo
-- ---------------------------------------------------------------------------
create table if not exists public.live_gifts (
    code text primary key,
    name text not null,
    emoji text not null,
    coins integer not null check (coins > 0),
    sort_order integer not null default 0,
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

alter table public.live_gifts enable row level security;

drop policy if exists "Authenticated users can select live gifts" on public.live_gifts;
create policy "Authenticated users can select live gifts"
    on public.live_gifts
    for select
    to authenticated
    using (is_active);

insert into public.live_gifts (code, name, emoji, coins, sort_order) values
    ('rose',     'Rosa',      '🌹', 1,    10),
    ('heart',    'Corazón',   '❤️', 5,    20),
    ('applause', 'Aplauso',   '👏', 10,   30),
    ('star',     'Estrella',  '⭐', 50,   40),
    ('crown',    'Corona',    '👑', 200,  50),
    ('diamond',  'Diamante',  '💎', 500,  60),
    ('rocket',   'Cohete',    '🚀', 1000, 70)
on conflict (code) do update
    set name = excluded.name,
        emoji = excluded.emoji,
        coins = excluded.coins,
        sort_order = excluded.sort_order,
        is_active = true;

create table if not exists public.live_gift_events (
    id uuid primary key default gen_random_uuid(),
    stream_id uuid not null references public.live_streams(id) on delete cascade,
    sender_id uuid not null references auth.users(id) on delete cascade,
    gift_code text not null references public.live_gifts(code),
    quantity integer not null default 1 check (quantity > 0),
    coins_total bigint not null default 0,
    created_at timestamptz not null default now()
);

create index if not exists idx_live_gift_events_stream on public.live_gift_events(stream_id, created_at desc);

alter table public.live_gift_events enable row level security;

drop policy if exists "Authenticated users can select live gift events" on public.live_gift_events;
create policy "Authenticated users can select live gift events"
    on public.live_gift_events
    for select
    to authenticated
    using (true);

-- ---------------------------------------------------------------------------
-- 4) Wallet de monedas (los regalos se pagan con monedas)
-- ---------------------------------------------------------------------------
create table if not exists public.user_wallets (
    user_id uuid primary key references auth.users(id) on delete cascade,
    coins integer not null default 0 check (coins >= 0),
    updated_at timestamptz not null default now()
);

alter table public.user_wallets enable row level security;

drop policy if exists "Users can select own wallet" on public.user_wallets;
create policy "Users can select own wallet"
    on public.user_wallets
    for select
    to authenticated
    using (user_id = auth.uid());

-- ---------------------------------------------------------------------------
-- 5) live_comments: eventos de sistema (uniones) + moderación del host
-- ---------------------------------------------------------------------------
alter table public.live_comments
    add column if not exists kind text not null default 'chat';

do $$
begin
    if not exists (
        select 1 from pg_constraint
        where conrelid = 'public.live_comments'::regclass
          and conname = 'live_comments_kind_check'
    ) then
        alter table public.live_comments
            add constraint live_comments_kind_check check (kind in ('chat', 'join'));
    end if;
end $$;

-- La moderación del host (marcar is_deleted) no tenía policy de UPDATE: el borrado
-- fallaba en silencio por RLS.
drop policy if exists "Host can moderate live comments" on public.live_comments;
create policy "Host can moderate live comments"
    on public.live_comments
    for update
    to authenticated
    using (
        exists (
            select 1 from public.live_streams s
            where s.id = stream_id and s.host_id = auth.uid()
        )
    )
    with check (
        exists (
            select 1 from public.live_streams s
            where s.id = stream_id and s.host_id = auth.uid()
        )
    );

-- ---------------------------------------------------------------------------
-- 6) RPCs
-- ---------------------------------------------------------------------------
create or replace function public.live_ensure_wallet(p_user_id uuid)
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_coins integer;
begin
    insert into public.user_wallets (user_id, coins)
    values (p_user_id, 5000)
    on conflict (user_id) do nothing;

    select coins into v_coins from public.user_wallets where user_id = p_user_id;
    return coalesce(v_coins, 0);
end;
$$;

create or replace function public.live_wallet_balance()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_coins integer;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;
    v_coins := public.live_ensure_wallet(v_uid);
    return jsonb_build_object('ok', true, 'balance', v_coins);
end;
$$;

create or replace function public.live_send_like(
    p_stream_id uuid,
    p_quantity integer default 1
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_qty integer := greatest(1, least(coalesce(p_quantity, 1), 500));
    v_total integer;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    if not exists (select 1 from public.live_streams where id = p_stream_id) then
        raise exception 'unknown_stream';
    end if;

    insert into public.live_reactions (stream_id, user_id, quantity)
    values (p_stream_id, v_uid, v_qty);

    insert into public.live_stream_stats (stream_id, like_count, updated_at)
    values (p_stream_id, v_qty, now())
    on conflict (stream_id) do update
        set like_count = public.live_stream_stats.like_count + v_qty,
            updated_at = now()
    returning like_count into v_total;

    return jsonb_build_object('ok', true, 'like_count', v_total);
end;
$$;

create or replace function public.live_send_gift(
    p_stream_id uuid,
    p_gift_code text,
    p_quantity integer default 1
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_price integer;
    v_qty integer := greatest(1, least(coalesce(p_quantity, 1), 100));
    v_total integer;
    v_balance integer;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    if not exists (select 1 from public.live_streams where id = p_stream_id) then
        raise exception 'unknown_stream';
    end if;

    select coins into v_price
      from public.live_gifts
     where code = p_gift_code and is_active;

    if v_price is null then
        raise exception 'unknown_gift';
    end if;

    v_total := v_price * v_qty;

    perform public.live_ensure_wallet(v_uid);

    update public.user_wallets
       set coins = coins - v_total,
           updated_at = now()
     where user_id = v_uid
       and coins >= v_total
    returning coins into v_balance;

    if v_balance is null then
        select coins into v_balance from public.user_wallets where user_id = v_uid;
        return jsonb_build_object(
            'ok', false,
            'reason', 'insufficient_funds',
            'balance', coalesce(v_balance, 0)
        );
    end if;

    insert into public.live_gift_events (stream_id, sender_id, gift_code, quantity, coins_total)
    values (p_stream_id, v_uid, p_gift_code, v_qty, v_total);

    insert into public.live_stream_stats (stream_id, gift_count, gift_coins, updated_at)
    values (p_stream_id, v_qty, v_total, now())
    on conflict (stream_id) do update
        set gift_count = public.live_stream_stats.gift_count + v_qty,
            gift_coins = public.live_stream_stats.gift_coins + v_total,
            updated_at = now();

    return jsonb_build_object('ok', true, 'balance', v_balance, 'total', v_total);
end;
$$;

create or replace function public.live_set_viewer_count(
    p_stream_id uuid,
    p_count integer
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_count integer := greatest(0, coalesce(p_count, 0));
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    insert into public.live_stream_stats (stream_id, viewer_count, updated_at)
    values (p_stream_id, v_count, now())
    on conflict (stream_id) do update
        set viewer_count = excluded.viewer_count,
            updated_at = now();
end;
$$;

-- Registra la entrada de un espectador como evento real del chat, sin spamear si
-- el usuario reconecta dentro de la misma sesión de dos horas.
create or replace function public.live_join_stream(p_stream_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_recent boolean;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    select exists (
        select 1 from public.live_comments
         where stream_id = p_stream_id
           and user_id = v_uid
           and kind = 'join'
           and created_at > now() - interval '2 hours'
    ) into v_recent;

    if v_recent then
        return jsonb_build_object('ok', true, 'inserted', false);
    end if;

    insert into public.live_comments (stream_id, user_id, text, kind)
    values (p_stream_id, v_uid, '', 'join');

    return jsonb_build_object('ok', true, 'inserted', true);
end;
$$;

-- ---------------------------------------------------------------------------
-- 7) Permisos
-- ---------------------------------------------------------------------------
revoke all on function public.live_ensure_wallet(uuid) from public, anon;
revoke all on function public.live_wallet_balance() from public, anon;
revoke all on function public.live_send_like(uuid, integer) from public, anon;
revoke all on function public.live_send_gift(uuid, text, integer) from public, anon;
revoke all on function public.live_set_viewer_count(uuid, integer) from public, anon;
revoke all on function public.live_join_stream(uuid) from public, anon;

grant execute on function public.live_wallet_balance() to authenticated;
grant execute on function public.live_send_like(uuid, integer) to authenticated;
grant execute on function public.live_send_gift(uuid, text, integer) to authenticated;
grant execute on function public.live_set_viewer_count(uuid, integer) to authenticated;
grant execute on function public.live_join_stream(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 8) Realtime
-- ---------------------------------------------------------------------------
do $$
declare
    v_table text;
begin
    foreach v_table in array array['live_stream_stats', 'live_gift_events']
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
