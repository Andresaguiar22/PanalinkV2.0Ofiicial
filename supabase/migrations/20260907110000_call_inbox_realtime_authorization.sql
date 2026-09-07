-- Call inbox — Realtime Broadcast authorization.
-- Escucha: SOLO el dueno del inbox puede escuchar su canal de llamadas.

create policy call_inbox_broadcast_receive
  on realtime.messages
  for select to authenticated
  using (
    realtime.topic() = 'realtime:call_inbox:' || (select auth.uid())::text
  );