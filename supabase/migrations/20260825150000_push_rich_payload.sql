-- Push real con app cerrada: payload rico + preview seguro para E2EE.
--
-- Causa raíz del "no suena con la app cerrada": la edge function send-push
-- desplegada rechazaba el payload de los triggers ({user_id,title,body}) y
-- además exigía JWT en el gateway (los triggers no mandan Authorization).
-- Esta migración reescribe los 3 triggers para mandar además:
--   - channel: canal Android que debe sonar
--   - data: notification_type, chat_id, sender_id... para que el cliente
--     pueda enrutar el tap y aplicar sus propias reglas (mute, chat activo)
--   - body: preview seguro. Los textos DM van cifrados (E2EE, Base64 sin
--     espacios) -> nunca se expone ciphertext en la bandeja; se manda un
--     placeholder. Media -> texto por tipo (📷 Foto, etc.).


-- El secreto interno NO puede vivir en un GUC app.* (custom GUCs requieren
-- superuser y la API de queries no lo permite). Se guarda en una tabla del
-- schema private, sin acceso para roles de API, leida por la funcion
-- security definer private.get_edge_secret().
create schema if not exists private;

create table if not exists private.edge_secrets (
  key text primary key,
  value text not null
);

revoke all on private.edge_secrets from public, anon, authenticated;

create or replace function private.get_edge_secret()
returns text
language sql
stable
security definer
set search_path = 'pg_catalog', 'private'
as $$
  select value from private.edge_secrets where key = 'edge_secret';
$$;

revoke all on function private.get_edge_secret() from public, anon, authenticated;

create or replace function public.fcm_notify_on_new_message()
returns trigger
language plpgsql
security definer
set search_path = 'pg_catalog', 'public', 'social', 'auth', 'realtime', 'extensions', 'net', 'pg_temp'
as $$
declare
  v_recipient_id uuid;
  v_sender_name text;
  v_project_ref text := 'tivqjfgjdxgzicrridaz';
  v_payload jsonb;
  v_edge_secret text;
  v_msg_type text;
  v_body text;
begin
  v_edge_secret := private.get_edge_secret();
  if v_edge_secret is null or v_edge_secret = '' then
    return new;
  end if;

  select case when user_a = new.sender_id then user_b else user_a end
    into v_recipient_id
    from public.one_to_one_threads
   where id = new.thread_id;

  if v_recipient_id is null then
    return new;
  end if;

  select display_name into v_sender_name
    from public.profiles
   where id = new.sender_id;

  v_sender_name := coalesce(v_sender_name, 'Mensaje nuevo');
  v_msg_type := coalesce(new.message_type, 'text');

  -- Preview seguro: por tipo, y sin exponer ciphertext E2EE.
  if v_msg_type <> 'text' then
    v_body := case v_msg_type
      when 'image' then '📷 Foto'
      when 'video' then '🎥 Video'
      when 'voice' then '🎤 Nota de voz'
      when 'audio' then '🎵 Audio'
      when 'document' then '📄 Documento'
      when 'sticker' then 'Sticker'
      when 'gif' then 'GIF'
      when 'call' then '📞 Llamada'
      when 'playlist' then '🎵 Playlist'
      when 'ghost' then '👻 Mensaje fantasma'
      else 'Nuevo mensaje'
    end;
  elsif new.text_content is null or new.text_content = '' then
    v_body := 'Nuevo mensaje';
  elsif length(new.text_content) > 40
        and new.text_content ~ '^[A-Za-z0-9+/]+={0,2}$' then
    -- Base64 IV+ciphertext del E2EE: no filtrar a la bandeja.
    v_body := '🔒 Nuevo mensaje';
  else
    v_body := left(new.text_content, 120);
  end if;

  v_payload := jsonb_build_object(
    'user_id', v_recipient_id,
    'title', v_sender_name,
    'body', v_body,
    'channel', 'panalink_messages_v3',
    'data', jsonb_build_object(
      'notification_type', 'new_message',
      'chat_id', coalesce(new.chat_id::text, ''),
      'thread_id', coalesce(new.thread_id::text, ''),
      'sender_id', new.sender_id,
      'sender_name', v_sender_name,
      'message_type', v_msg_type,
      'message_id', new.id::text,
      'client_message_uuid', coalesce(new.client_message_uuid::text, '')
    )
  );

  begin
    perform net.http_post(
      url := 'https://' || v_project_ref || '.functions.supabase.co/send-push',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'x-internal-secret', v_edge_secret
      ),
      body := v_payload
    );
  exception when others then
    raise warning 'FCM notification failed for thread message %, transaction continues: %', new.id, sqlerrm;
  end;

  return new;
end;
$$;

create or replace function public.fcm_notify_on_friend_request()
returns trigger
language plpgsql
security definer
set search_path = 'pg_catalog', 'public', 'social', 'auth', 'realtime', 'extensions', 'net', 'pg_temp'
as $$
declare
  v_sender_name text;
  v_project_ref text := 'tivqjfgjdxgzicrridaz';
  v_payload jsonb;
  v_edge_secret text;
begin
  v_edge_secret := private.get_edge_secret();
  if v_edge_secret is null or v_edge_secret = '' then
    return new;
  end if;

  select display_name into v_sender_name
    from public.profiles
   where id = new.sender_id;

  v_sender_name := coalesce(v_sender_name, 'Alguien');
  v_payload := jsonb_build_object(
    'user_id', new.receiver_id,
    'title', v_sender_name,
    'body', v_sender_name || ' quiere ser tu Pana. Toca para aceptar la solicitud.',
    'channel', 'panalink_alerts_v3',
    'data', jsonb_build_object(
      'notification_type', 'friend_request',
      'sender_id', new.sender_id,
      'sender_name', v_sender_name,
      'request_id', new.id::text
    )
  );

  begin
    perform net.http_post(
      url := 'https://' || v_project_ref || '.functions.supabase.co/send-push',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'x-internal-secret', v_edge_secret
      ),
      body := v_payload
    );
  exception when others then
    raise warning 'FCM notification failed for friend_request %, transaction continues: %', new.id, sqlerrm;
  end;

  return new;
end;
$$;

create or replace function public.fcm_notify_on_friend_request_accepted()
returns trigger
language plpgsql
security definer
set search_path = 'pg_catalog', 'public', 'social', 'auth', 'realtime', 'extensions', 'net', 'pg_temp'
as $$
declare
  v_receiver_name text;
  v_project_ref text := 'tivqjfgjdxgzicrridaz';
  v_payload jsonb;
  v_edge_secret text;
begin
  if not (old.status is distinct from new.status and new.status = 'accepted') then
    return new;
  end if;

  v_edge_secret := private.get_edge_secret();
  if v_edge_secret is null or v_edge_secret = '' then
    return new;
  end if;

  select display_name into v_receiver_name
    from public.profiles
   where id = new.receiver_id;

  v_receiver_name := coalesce(v_receiver_name, 'Tu Pana');
  v_payload := jsonb_build_object(
    'user_id', new.sender_id,
    'title', v_receiver_name,
    'body', v_receiver_name || ' aceptó tu solicitud. ¡Ya son Panas!',
    'channel', 'panalink_alerts_v3',
    'data', jsonb_build_object(
      'notification_type', 'friend_request_accepted',
      'sender_id', new.receiver_id,
      'sender_name', v_receiver_name,
      'request_id', new.id::text
    )
  );

  begin
    perform net.http_post(
      url := 'https://' || v_project_ref || '.functions.supabase.co/send-push',
      headers := jsonb_build_object(
        'Content-Type', 'application/json',
        'x-internal-secret', v_edge_secret
      ),
      body := v_payload
    );
  exception when others then
    raise warning 'FCM notification failed for friend_request accepted %, transaction continues: %', new.id, sqlerrm;
  end;

  return new;
end;
$$;
