-- Harden Live engagement RPCs so post-END streams cannot receive new engagement.
-- The Live UI already gates these actions, but the database must enforce the invariant.

create or replace function public.live_join_stream(p_stream_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_uid uuid := auth.uid();
    v_recent boolean;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    if not exists (
        select 1 from public.live_streams
         where id = p_stream_id
           and status = 'LIVE'
    ) then
        raise exception 'stream_not_live';
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
$function$;

create or replace function public.live_send_gift(
    p_stream_id uuid,
    p_gift_code text,
    p_quantity integer default 1
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
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

    if not exists (
        select 1 from public.live_streams
         where id = p_stream_id
           and status = 'LIVE'
    ) then
        raise exception 'stream_not_live';
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
$function$;

create or replace function public.live_send_like(
    p_stream_id uuid,
    p_quantity integer default 1
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_uid uuid := auth.uid();
    v_qty integer := greatest(1, least(coalesce(p_quantity, 1), 500));
    v_total integer;
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    if not exists (
        select 1 from public.live_streams
         where id = p_stream_id
           and status = 'LIVE'
    ) then
        raise exception 'stream_not_live';
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
$function$;

create or replace function public.live_set_viewer_count(
    p_stream_id uuid,
    p_count integer
)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_uid uuid := auth.uid();
    v_count integer := greatest(0, coalesce(p_count, 0));
begin
    if v_uid is null then
        raise exception 'not_authenticated';
    end if;

    if not exists (
        select 1 from public.live_streams
         where id = p_stream_id
           and status = 'LIVE'
    ) then
        raise exception 'stream_not_live';
    end if;

    insert into public.live_stream_stats (stream_id, viewer_count, updated_at)
    values (p_stream_id, v_count, now())
    on conflict (stream_id) do update
        set viewer_count = excluded.viewer_count,
            updated_at = now();
end;
$function$;

revoke execute on function public.live_join_stream(uuid) from public, anon;
grant execute on function public.live_join_stream(uuid) to authenticated;

revoke execute on function public.live_send_gift(uuid,text,integer) from public, anon;
grant execute on function public.live_send_gift(uuid,text,integer) to authenticated;

revoke execute on function public.live_send_like(uuid,integer) from public, anon;
grant execute on function public.live_send_like(uuid,integer) to authenticated;

revoke execute on function public.live_set_viewer_count(uuid,integer) from public, anon;
grant execute on function public.live_set_viewer_count(uuid,integer) to authenticated;
