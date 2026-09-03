-- Salas de Voz — Fase 1.2 (correcciones). Todo es idempotente.
--
-- 1) REPLICA IDENTITY FULL: sin esto, los eventos DELETE de realtime solo llevan
--    la PK en old_record y los clientes no reciben user_id/room_id al liberarse
--    un sillon (PeerConnections y sillones quedan zombies en los demas clientes).
-- 2) Politicas de broadcast Realtime Authorization acotadas a voice_room:{roomId}
--    (solo si el proyecto tiene realtime.messages; no toca politicas del chat).
-- 3) Lobby get-or-create seguro a nivel DB (indice unico parcial + RPC atomica).
-- 4) Coherencia de roles: sentado = 'speaker', de pie = 'listener' (trigger).

-- ============================================================
-- 1) Replica identity
-- ============================================================
-- voice_room_seats: el cliente consume DELETE y necesita user_id de old_record.
alter table public.voice_room_seats replica identity full;
-- voice_room_members: el trigger de roles genera UPDATEs; se mantiene FULL para
-- que cualquier consumidor reciba la fila completa en DELETE/UPDATE.
alter table public.voice_room_members replica identity full;
-- voice_room_messages: el modulo solo consume INSERT (record completo por defecto).
-- voice_rooms: el modulo no se suscribe a cambios de salas.
-- Por eso ambas NO necesitan REPLICA IDENTITY FULL en esta fase.

-- ============================================================
-- 2) Broadcast Realtime Authorization (solo canales de salas de voz)
-- ============================================================
-- Verificado en vivo contra el proyecto (2026-08-23): Realtime Authorization
-- NO esta habilitado. Un join+broadcast a un canal voice_room con solo la anon
-- key (sin JWT de usuario y sin ninguna politica sobre realtime.messages)
-- funciona; el chat ademas usa broadcast (typing/presence/reactions) sin
-- config.private ni politicas. Por tanto broadcast NO requiere politicas hoy.
--
-- Este bloque es DEFENSIVO y acotado: solo si existe realtime.messages
-- (feature de autorizacion presente), crea UNICAMENTE las politicas de los
-- topics de salas de voz ("realtime:voice_room:{roomId}"; se acepta tambien
-- "voice_room:{roomId}" por compatibilidad), restringidas a miembros activos
-- de la sala. No otorga permisos globales ni toca politicas del chat.
-- Si realtime.messages no existe, es un no-op.
do $voice_room_rl$
begin
  if exists (select 1 from pg_tables where schemaname = 'realtime' and tablename = 'messages')
     and exists (
       select 1 from pg_proc p
         join pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'realtime' and p.proname = 'topic'
     ) then

    execute $p$
      drop policy if exists voice_room_broadcast_receive on realtime.messages
    $p$;
    execute $p$
      create policy voice_room_broadcast_receive on realtime.messages
        for select to authenticated
        using (
          realtime.topic() ~ '(^|:)voice_room:[0-9a-fA-F-]{36}$'
          and public.is_voice_room_member(
                substring(realtime.topic() from 'voice_room:([0-9a-fA-F-]{36})')::uuid)
        )
    $p$;

    execute $p$
      drop policy if exists voice_room_broadcast_send on realtime.messages
    $p$;
    execute $p$
      create policy voice_room_broadcast_send on realtime.messages
        for insert to authenticated
        with check (
          realtime.topic() ~ '(^|:)voice_room:[0-9a-fA-F-]{36}$'
          and public.is_voice_room_member(
                substring(realtime.topic() from 'voice_room:([0-9a-fA-F-]{36})')::uuid)
        )
    $p$;
  end if;
end
$voice_room_rl$;

-- ============================================================
-- 3) Lobby unico (get-or-create seguro a nivel DB)
-- ============================================================
-- Garantia dura: a lo sumo UNA sala con status='live' en toda la tabla
-- (Fase 1: una sola sala publica). Evita la carrera SELECT -> INSERT.
create unique index if not exists voice_rooms_single_live_room
  on public.voice_rooms ((1))
  where status = 'live';

-- RPC atomica: serializa creadores concurrentes con un advisory lock de
-- transaccion y, como red de seguridad, captura unique_violation y re-lee.
create or replace function public.get_or_create_voice_lobby()
returns setof public.voice_rooms
language plpgsql
security definer
set search_path = 'pg_catalog', 'public'
as $voice_room_lobby$
declare
  v_room public.voice_rooms%rowtype;
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;

  perform pg_advisory_xact_lock(hashtextextended('voice_room_lobby_singleton', 0));

  select * into v_room
    from public.voice_rooms
   where status = 'live'
   order by created_at asc
   limit 1;
  if found then
    return next v_room;
    return;
  end if;

  begin
    insert into public.voice_rooms (name, owner_id)
    values ('Sala Principal', auth.uid())
    returning * into v_room;
  exception
    when unique_violation then
      select * into v_room
        from public.voice_rooms
       where status = 'live'
       order by created_at asc
       limit 1;
  end;

  return next v_room;
end;
$voice_room_lobby$;

revoke all on function public.get_or_create_voice_lobby() from public;
grant execute on function public.get_or_create_voice_lobby() to authenticated;

-- ============================================================
-- 4) Coherencia de roles: sillon ocupado = speaker, libre = listener
-- ============================================================
-- El role 'owner' nunca se toca: solo se alterna listener <-> speaker.
create or replace function public.voice_room_seat_role_sync()
returns trigger
language plpgsql
security definer
set search_path = 'pg_catalog', 'public'
as $voice_room_role$
begin
  if TG_OP = 'INSERT' then
    update public.voice_room_members
       set role = 'speaker'
     where room_id = NEW.room_id
       and user_id = NEW.user_id
       and left_at is null
       and role = 'listener';
    return NEW;
  elsif TG_OP = 'DELETE' then
    update public.voice_room_members
       set role = 'listener'
     where room_id = OLD.room_id
       and user_id = OLD.user_id
       and left_at is null
       and role = 'speaker';
    return OLD;
  end if;
  return null;
end;
$voice_room_role$;

drop trigger if exists voice_room_seat_role_sync on public.voice_room_seats;
create trigger voice_room_seat_role_sync
  after insert or delete on public.voice_room_seats
  for each row execute function public.voice_room_seat_role_sync();
