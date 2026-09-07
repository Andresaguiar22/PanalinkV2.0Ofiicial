-- Call logs: defensive RLS hardening for tables created outside migrations.
-- call_sessions and call_signaling_events fuel notification triggers but their
-- DDL/RLS is not versioned, so harden whatever exists in the deployed DB.
-- Idempotent: each step is guarded by to_regclass / information_schema.

do $x$
begin

  if to_regclass('public.call_sessions') is not null then
    alter table public.call_sessions enable row level security;
    revoke all on public.call_sessions from anon,public;
    grant select,insert on public.call_sessions to authenticated;

    if exists(select 1 from information_schema.columns
      where table_schema='public' and table_name='call_sessions'
        and column_name in('created_by','participant_a','participant_b')) then
      execute format('drop policy if exists call_sessions_select_participant on public.call_sessions');
      execute format('create policy call_sessions_select_participant on public.call_sessions for select to authenticated using (created_by=(select auth.uid()) or participant_a=(select auth.uid()) or participant_b=(select auth.uid()))');
      execute format('drop policy if exists call_sessions_insert_creator on public.call_sessions');
      execute format('create policy call_sessions_insert_creator on public.call_sessions for insert to authenticated with check (created_by=(select auth.uid()))');
    end if;
  end if;

  if to_regclass('public.call_signaling_events') is not null then
    alter table public.call_signaling_events enable row level security;
    revoke all on public.call_signaling_events from anon,public;
    grant select,insert on public.call_signaling_events to authenticated;

    if exists(select 1 from information_schema.columns
      where table_schema='public' and table_name='call_signaling_events'
        and column_name in('session_id','sender_id')) then
      execute format('drop policy if exists call_signaling_events_participant on public.call_signaling_events');
      execute format('create policy call_signaling_events_participant on public.call_signaling_events for select to authenticated using (exists(select 1 from public.call_sessions s where s.id=session_id and (s.created_by=(select auth.uid()) or s.participant_a=(select auth.uid()) or s.participant_b=(select auth.uid()))))');
      execute format('drop policy if exists call_signaling_events_participant_insert on public.call_signaling_events');
      execute format('create policy call_signaling_events_participant_insert on public.call_signaling_events for insert to authenticated with check (exists(select 1 from public.call_sessions s where s.id=session_id and (s.created_by=(select auth.uid()) or s.participant_a=(select auth.uid()) or s.participant_b=(select auth.uid()))))');
    end if;
  end if;

end $x$;