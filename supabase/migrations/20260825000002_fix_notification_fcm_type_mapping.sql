create or replace function private.dispatch_panalink_notification(
  p_recipient uuid,
  p_actor uuid,
  p_type text,
  p_domain text,
  p_title text,
  p_body text,
  p_entity_id uuid default null,
  p_entity_type text default null,
  p_channel text default 'panalink_alerts_v3',
  p_priority text default 'HIGH',
  p_grouping_key text default null,
  p_payload jsonb default '{}'::jsonb
)
returns void
language plpgsql
security definer
set search_path = 'pg_catalog','public','social','social_events','auth','net','private','pg_temp'
as $fn$
declare v_secret text; v_sender_name text; v_payload jsonb; v_notification_id uuid;
begin
  if p_recipient is null or (p_actor is not null and p_recipient=p_actor) then return; end if;
  if exists(select 1 from public.notification_preferences np where np.user_id=p_recipient and (upper(np.domain)=upper(coalesce(p_domain,'SYSTEM')) or upper(np.domain)='ALL') and np.enabled=false) then return; end if;
  if p_actor is not null and exists(select 1 from social_events.blocked_users b where (b.blocker_user_id=p_recipient and b.blocked_user_id=p_actor) or (b.blocker_user_id=p_actor and b.blocked_user_id=p_recipient)) then return; end if;
  select display_name into v_sender_name from public.profiles where id=p_actor;
  v_payload := coalesce(p_payload,'{}'::jsonb) || jsonb_build_object('notification_type',lower(p_type),'domain',upper(p_domain),'entity_id',coalesce(p_entity_id::text,''),'entity_type',coalesce(p_entity_type,''),'actor_id',coalesce(p_actor::text,''),'actor_name',coalesce(v_sender_name,''),'title',p_title,'body',p_body);
  insert into public.notifications_v2(recipient_id,actor_id,domain,type,entity_id,entity_type,title,body,payload,is_read,grouping_key,priority,source_event_id)
  values(p_recipient,p_actor,upper(p_domain),upper(p_type),p_entity_id,p_entity_type,p_title,p_body,v_payload,false,coalesce(p_grouping_key,upper(p_type)||':'||coalesce(p_entity_id::text,'')),p_priority,gen_random_uuid()) returning id into v_notification_id;
  v_secret := private.get_edge_secret();
  if v_secret is null or v_secret='' then return; end if;
  perform net.http_post(url:='https://tivqjfgjdxgzicrridaz.functions.supabase.co/send-push',headers:=jsonb_build_object('Content-Type','application/json','x-internal-secret',v_secret),body:=jsonb_build_object('user_id',p_recipient,'title',p_title,'body',p_body,'channel',p_channel,'data',v_payload || jsonb_build_object('notification_id',v_notification_id::text)));
exception when others then
  raise warning 'Panalink notification dispatch failed type=% recipient=%: %',p_type,p_recipient,sqlerrm;
end;
$fn$;
revoke all on function private.dispatch_panalink_notification(uuid,uuid,text,text,text,text,uuid,text,text,text,text,jsonb) from public,anon,authenticated;
