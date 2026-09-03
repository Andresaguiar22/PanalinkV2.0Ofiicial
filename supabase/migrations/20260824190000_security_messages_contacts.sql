-- Panalink: seguridad de mensajes privados + compatibilidad PIN/QR.
-- La vista public.messages expone thread_messages; se obliga a respetar RLS.
create or replace view public.messages
with (security_invoker = true)
as
select id, thread_id, sender_id, receiver_id, chat_id, message_type,
       text_content, media_url, thumbnail_url, media_mime, created_at,
       file_size, duration, width, height, reply_to, client_message_uuid
from public.thread_messages;

-- Compatibilidad segura del RPC antiguo: acepta PIN crudo de 6 dígitos o hash SHA-256.
create or replace function public.add_contact_by_pin(p_pin text)
returns uuid
language plpgsql
security definer
set search_path = 'pg_catalog', 'public', 'pg_temp'
as $$
declare
  v_contact_id uuid;
  v_owner_id uuid := auth.uid();
  v_pin_hash text;
begin
  if v_owner_id is null then raise exception 'Not authenticated'; end if;
  if p_pin is null or length(trim(p_pin)) = 0 then raise exception 'PIN requerido'; end if;
  if trim(p_pin) ~ '^[0-9]{6}$' then
    v_pin_hash := encode(digest(trim(p_pin), 'sha256'), 'hex');
  elsif trim(p_pin) ~ '^[0-9a-fA-F]{64}$' then
    v_pin_hash := lower(trim(p_pin));
  else
    raise exception 'PIN inválido';
  end if;
  select ci.user_id into v_contact_id from public.contact_identifiers ci
  where ci.pin_hash = v_pin_hash and ci.revoked_at is null limit 1;
  if v_contact_id is null then raise exception 'PIN inválido o revocado'; end if;
  if v_contact_id = v_owner_id then raise exception 'No puedes agregarte a ti mismo'; end if;
  insert into public.contacts(owner_user_id, contact_user_id)
  values (v_owner_id, v_contact_id)
  on conflict (owner_user_id, contact_user_id) do nothing;
  return v_contact_id;
end;
$$;

revoke all on function public.add_contact_by_pin(text) from public;
grant execute on function public.add_contact_by_pin(text) to authenticated;

-- Ruta unificada PIN/QR. PIN usa SHA-256; QR usa qr_token.
create or replace function public.add_contact_by_identifier(p_identifier text)
returns jsonb
language plpgsql
security definer
set search_path = 'public', 'pg_temp'
as $$
declare
  v_uid uuid := auth.uid();
  v_contact_user_id uuid;
  v_pin_hash text;
  v_qr_token text;
  v_is_pin boolean;
  v_display_name text;
  v_avatar_url text;
  v_thread_id uuid;
  v_a uuid;
  v_b uuid;
  v_is_already_contact boolean := false;
begin
  if v_uid is null then raise exception 'Not authenticated'; end if;
  if p_identifier is null or length(trim(p_identifier)) = 0 then
    return jsonb_build_object('success',false,'error','identifier required');
  end if;
  v_is_pin := trim(p_identifier) ~ '^[0-9]{6}$';
  if v_is_pin then
    v_pin_hash := encode(digest(trim(p_identifier), 'sha256'), 'hex');
    select ci.user_id into v_contact_user_id from public.contact_identifiers ci
    where ci.pin_hash = v_pin_hash and ci.revoked_at is null limit 1;
  else
    v_qr_token := case when trim(p_identifier) like 'panalink:contact:v1:%'
      then split_part(trim(p_identifier), ':', 4) else trim(p_identifier) end;
    select ci.user_id into v_contact_user_id from public.contact_identifiers ci
    where ci.qr_token = v_qr_token and ci.revoked_at is null limit 1;
  end if;
  if v_contact_user_id is null then return jsonb_build_object('success',false,'error','identifier not found'); end if;
  if v_contact_user_id = v_uid then return jsonb_build_object('success',false,'error','self contact not allowed'); end if;
  select exists(select 1 from public.contacts c where c.owner_user_id=v_uid and c.contact_user_id=v_contact_user_id) into v_is_already_contact;
  v_a := least(v_uid,v_contact_user_id); v_b := greatest(v_uid,v_contact_user_id);
  select t.id into v_thread_id from public.one_to_one_threads t where t.user_a=v_a and t.user_b=v_b limit 1;
  if v_thread_id is null then
    insert into public.one_to_one_threads(user_a,user_b) values(v_a,v_b) on conflict do nothing returning id into v_thread_id;
    if v_thread_id is null then select t.id into v_thread_id from public.one_to_one_threads t where t.user_a=v_a and t.user_b=v_b limit 1; end if;
  end if;
  if not v_is_already_contact then
    insert into public.contacts(owner_user_id,contact_user_id) values(v_uid,v_contact_user_id) on conflict(owner_user_id,contact_user_id) do nothing;
  end if;
  select pr.display_name,pr.avatar_url into v_display_name,v_avatar_url from public.profiles pr where pr.id=v_contact_user_id limit 1;
  return jsonb_build_object('success',true,'contact_id',v_contact_user_id,'thread_id',v_thread_id,'display_name',coalesce(v_display_name,'Pana'),'avatar_url',v_avatar_url,'is_already_contact',v_is_already_contact);
end;
$$;

create or replace function public.resolve_contact_identifier(p_identifier text)
returns jsonb
language plpgsql
security definer
set search_path = 'public', 'pg_temp'
as $$
declare
  v_identifier text := trim(coalesce(p_identifier,''));
  v_user_id uuid;
  v_display_name text;
  v_avatar_url text;
  v_pin_hash text;
  v_qr_token text;
begin
  if v_identifier='' then return jsonb_build_object('user_id',null,'display_name',null,'avatar_url',null); end if;
  if v_identifier ~ '^[0-9]{6}$' then
    v_pin_hash := encode(digest(v_identifier,'sha256'),'hex');
    select ci.user_id into v_user_id from public.contact_identifiers ci where ci.pin_hash=v_pin_hash and ci.revoked_at is null limit 1;
  else
    v_qr_token := case when v_identifier like 'panalink:contact:v1:%' then split_part(v_identifier,':',4) else v_identifier end;
    select ci.user_id into v_user_id from public.contact_identifiers ci where ci.qr_token=v_qr_token and ci.revoked_at is null limit 1;
  end if;
  if v_user_id is null then return jsonb_build_object('user_id',null,'display_name',null,'avatar_url',null); end if;
  select pr.display_name,pr.avatar_url into v_display_name,v_avatar_url from public.profiles pr where pr.id=v_user_id limit 1;
  return jsonb_build_object('user_id',v_user_id,'display_name',v_display_name,'avatar_url',v_avatar_url);
end;
$$;

revoke all on function public.add_contact_by_identifier(text) from public;
grant execute on function public.add_contact_by_identifier(text) to authenticated;
revoke all on function public.resolve_contact_identifier(text) from public;
grant execute on function public.resolve_contact_identifier(text) to authenticated;
revoke all on function public.get_my_contact_identifier() from public;
grant execute on function public.get_my_contact_identifier() to authenticated;

alter table public.contact_identifiers enable row level security;
drop policy if exists contact_identifiers_select_own on public.contact_identifiers;
create policy contact_identifiers_select_own on public.contact_identifiers for select to authenticated using (user_id = (select auth.uid()));
drop policy if exists contact_identifiers_insert_own on public.contact_identifiers;
create policy contact_identifiers_insert_own on public.contact_identifiers for insert to authenticated with check (user_id = (select auth.uid()));
drop policy if exists contact_identifiers_update_own on public.contact_identifiers;
create policy contact_identifiers_update_own on public.contact_identifiers for update to authenticated using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));