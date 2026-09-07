-- Progressive read receipt: acknowledge one thread watermark instead of one RPC per message.
create or replace function public.mark_thread_read_through(
  p_thread_id uuid,
  p_before timestamptz
)
returns integer
language plpgsql
security definer
set search_path = 'public', 'pg_temp'
as $$
declare
  result integer := 0;
begin
  perform set_config('app.message_state_rpc', '1', true);

  update public.thread_messages
  set read_at = coalesce(read_at, now()),
      delivered_at = coalesce(delivered_at, now()),
      seen_at = coalesce(seen_at, now())
  where thread_id = p_thread_id
    and receiver_id = auth.uid()
    and sender_id <> auth.uid()
    and deleted_at is null
    and created_at <= p_before
    and (read_at is null or delivered_at is null or seen_at is null);

  get diagnostics result = row_count;
  return result;
end;
$$;

grant execute on function public.mark_thread_read_through(uuid, timestamptz) to authenticated;
revoke execute on function public.mark_thread_read_through(uuid, timestamptz) from anon;
