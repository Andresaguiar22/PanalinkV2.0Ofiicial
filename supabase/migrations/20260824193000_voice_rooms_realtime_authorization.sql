-- Voice Rooms — Realtime Broadcast authorization.
-- The Android signaling channel uses config.private=true for
-- realtime:voice_room:{roomId}. Only active room members may send/receive
-- ephemeral SDP/ICE/peer-reset signaling.

create policy voice_room_broadcast_receive
  on realtime.messages
  for select to authenticated
  using (
    realtime.topic() ~ '^realtime:voice_room:[0-9a-fA-F-]{36}$'
    and public.is_voice_room_member(
      substring(realtime.topic() from 'voice_room:([0-9a-fA-F-]{36})')::uuid
    )
  );

create policy voice_room_broadcast_send
  on realtime.messages
  for insert to authenticated
  with check (
    realtime.topic() ~ '^realtime:voice_room:[0-9a-fA-F-]{36}$'
    and public.is_voice_room_member(
      substring(realtime.topic() from 'voice_room:([0-9a-fA-F-]{36})')::uuid
    )
  );
