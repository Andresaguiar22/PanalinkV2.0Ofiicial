begin;

-- Every one_to_one_thread must have a canonical private chat mapping.
-- thread_messages.trg_thread_messages_set_chat_id relies on that mapping and
-- intentionally rejects DM inserts when it is missing. Keep this server-side
-- so authenticated clients do not need broad INSERT access to chats.
create or replace function public.ensure_private_chat_for_one_to_one_thread()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, pg_temp
as $$
declare
  v_chat_id uuid;
  v_lock_key bigint;
  v_pair_key text;
begin
  if new.user_a is null or new.user_b is null then
    raise exception 'one_to_one_thread participants are required';
  end if;

  v_pair_key := least(new.user_a::text, new.user_b::text) || ':' || greatest(new.user_a::text, new.user_b::text);
  v_lock_key := hashtextextended(v_pair_key, 0);
  perform pg_advisory_xact_lock(v_lock_key);

  select c.id
    into v_chat_id
    from public.chats c
   where c.chat_type = 'private'::public.chat_type
     and exists (
       select 1 from public.chat_participants cp
        where cp.chat_id = c.id
          and cp.user_id = new.user_a
          and cp.left_at is null
     )
     and exists (
       select 1 from public.chat_participants cp
        where cp.chat_id = c.id
          and cp.user_id = new.user_b
          and cp.left_at is null
     )
   order by c.created_at asc
   limit 1;

  if v_chat_id is null then
    insert into public.chats (chat_type, created_by)
    values ('private'::public.chat_type, new.user_a)
    returning id into v_chat_id;

    insert into public.chat_participants (chat_id, user_id)
    values
      (v_chat_id, new.user_a),
      (v_chat_id, new.user_b)
    on conflict (chat_id, user_id) do update
      set left_at = null;
  end if;

  return new;
end;
$$;

drop trigger if exists trg_one_to_one_threads_ensure_private_chat on public.one_to_one_threads;
create trigger trg_one_to_one_threads_ensure_private_chat
after insert on public.one_to_one_threads
for each row execute function public.ensure_private_chat_for_one_to_one_thread();

-- Backfill existing threads that predate the trigger.
do $$
declare
  t record;
  v_chat_id uuid;
  v_lock_key bigint;
  v_pair_key text;
begin
  for t in select id, user_a, user_b from public.one_to_one_threads loop
    v_pair_key := least(t.user_a::text, t.user_b::text) || ':' || greatest(t.user_a::text, t.user_b::text);
    v_lock_key := hashtextextended(v_pair_key, 0);
    perform pg_advisory_xact_lock(v_lock_key);

    select c.id
      into v_chat_id
      from public.chats c
     where c.chat_type = 'private'::public.chat_type
       and exists (
         select 1 from public.chat_participants cp
          where cp.chat_id = c.id
            and cp.user_id = t.user_a
            and cp.left_at is null
       )
       and exists (
         select 1 from public.chat_participants cp
          where cp.chat_id = c.id
            and cp.user_id = t.user_b
            and cp.left_at is null
       )
     order by c.created_at asc
     limit 1;

    if v_chat_id is null then
      insert into public.chats (chat_type, created_by)
      values ('private'::public.chat_type, t.user_a)
      returning id into v_chat_id;

      insert into public.chat_participants (chat_id, user_id)
      values
        (v_chat_id, t.user_a),
        (v_chat_id, t.user_b)
      on conflict (chat_id, user_id) do update
        set left_at = null;
    end if;
  end loop;
end;
$$;

revoke execute on function public.ensure_private_chat_for_one_to_one_thread() from public, anon, authenticated;

commit;
