-- Panalink production notification fan-out hardening.
-- Uses pg_net asynchronously so user writes are not blocked by FCM/network latency.
-- All delivery goes through the already deployed send-push gateway.

create schema if not exists private;

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
set search_path = 'pg_catalog', 'public', 'social', 'social_events', 'auth', 'net', 'private', 'pg_temp'
as $fn$
declare
  v_secret text;
  v_sender_name text;
  v_payload jsonb;
  v_notification_id uuid;
begin
  if p_recipient is null or p_actor is not null and p_recipient = p_actor then return; end if;
  if exists (select 1 from public.notification_preferences np where np.user_id=p_recipient and (upper(np.domain)=upper(coalesce(p_domain,'SYSTEM')) or upper(np.domain)='ALL') and np.enabled=false) then return; end if;
  if p_actor is not null and exists (select 1 from social_events.blocked_users b where (b.blocker_user_id=p_recipient and b.blocked_user_id=p_actor) or (b.blocker_user_id=p_actor and b.blocked_user_id=p_recipient)) then return; end if;

  select display_name into v_sender_name from public.profiles where id=p_actor;
  v_payload := coalesce(p_payload,'{}'::jsonb) || jsonb_build_object('notification_type',p_type,'domain',p_domain,'entity_id',coalesce(p_entity_id::text,''),'entity_type',coalesce(p_entity_type,''),'actor_id',coalesce(p_actor::text,''),'actor_name',coalesce(v_sender_name,''),'title',p_title,'body',p_body);

  insert into public.notifications_v2(recipient_id,actor_id,domain,type,entity_id,entity_type,title,body,payload,is_read,grouping_key,priority,source_event_id)
  values(p_recipient,p_actor,p_domain,p_type,p_entity_id,p_entity_type,p_title,p_body,v_payload,false,coalesce(p_grouping_key,p_type||':'||coalesce(p_entity_id::text,'')),p_priority,gen_random_uuid())
  returning id into v_notification_id;

  v_secret := private.get_edge_secret();
  if v_secret is null or v_secret='' then return; end if;
  perform net.http_post(
    url := 'https://tivqjfgjdxgzicrridaz.functions.supabase.co/send-push',
    headers := jsonb_build_object('Content-Type','application/json','x-internal-secret',v_secret),
    body := jsonb_build_object('user_id',p_recipient,'title',p_title,'body',p_body,'channel',p_channel,'data',v_payload || jsonb_build_object('notification_id',v_notification_id::text))
  );
exception when others then
  raise warning 'Panalink notification dispatch failed type=% recipient=%: %',p_type,p_recipient,sqlerrm;
end;
$fn$;

revoke all on function private.dispatch_panalink_notification(uuid,uuid,text,text,text,text,uuid,text,text,text,text,jsonb) from public;

create or replace function public.notify_on_post_like() returns trigger language plpgsql security definer set search_path='pg_catalog','public','private','social_events','pg_temp' as $fn$ declare v_owner uuid; begin select user_id into v_owner from public.posts where id=new.post_id; perform private.dispatch_panalink_notification(v_owner,new.user_id,'POST_LIKE','POSTS','Nuevo me gusta','A alguien le gustó tu publicación',new.post_id,'post','panalink_alerts_v3','HIGH','post_like:'||new.post_id::text,'{}'::jsonb); return new; end; $fn$;
create or replace function public.notify_on_post_comment() returns trigger language plpgsql security definer set search_path='pg_catalog','public','private','social_events','pg_temp' as $fn$ declare v_owner uuid; begin select user_id into v_owner from public.posts where id=new.post_id; perform private.dispatch_panalink_notification(v_owner,new.user_id,'POST_COMMENT','COMMENTS','Nuevo comentario',coalesce((select display_name from public.profiles where id=new.user_id),'Alguien')||' comentó tu publicación',new.post_id,'post','panalink_alerts_v3','HIGH','post_comment:'||new.post_id::text,jsonb_build_object('comment_id',new.id::text)); return new; end; $fn$;
create or replace function public.notify_on_reel_like() returns trigger language plpgsql security definer set search_path='pg_catalog','public','social','private','social_events','pg_temp' as $fn$ declare v_owner uuid; begin select author_id into v_owner from social.user_reels where id=new.reel_id; perform private.dispatch_panalink_notification(v_owner,new.user_id,'REEL_LIKE','REELS','Nuevo me gusta','A alguien le gustó tu reel',new.reel_id,'reel','panalink_alerts_v3','HIGH','reel_like:'||new.reel_id::text,'{}'::jsonb); return new; end; $fn$;
create or replace function public.notify_on_reel_comment() returns trigger language plpgsql security definer set search_path='pg_catalog','public','social','private','social_events','pg_temp' as $fn$ declare v_owner uuid; begin select author_id into v_owner from social.user_reels where id=new.reel_id; perform private.dispatch_panalink_notification(v_owner,new.author_id,'REEL_COMMENT','REELS','Nuevo comentario',coalesce((select display_name from public.profiles where id=new.author_id),'Alguien')||' comentó tu reel',new.reel_id,'reel','panalink_alerts_v3','HIGH','reel_comment:'||new.reel_id::text,jsonb_build_object('comment_id',new.id::text)); return new; end; $fn$;
create or replace function public.notify_on_reel_comment_reaction() returns trigger language plpgsql security definer set search_path='pg_catalog','public','social','private','social_events','pg_temp' as $fn$ declare v_owner uuid; v_reel uuid; begin select author_id,reel_id into v_owner,v_reel from social.reel_comments where id=new.comment_id; perform private.dispatch_panalink_notification(v_owner,new.user_id,'REEL_REACTION','REELS','Reacción a tu comentario','A alguien le gustó tu comentario',v_reel,'reel','panalink_alerts_v3','NORMAL','reel_comment_reaction:'||new.comment_id::text,jsonb_build_object('comment_id',new.comment_id::text,'reaction',new.reaction)); return new; end; $fn$;
create or replace function public.notify_on_story_like() returns trigger language plpgsql security definer set search_path='pg_catalog','public','social','private','social_events','pg_temp' as $fn$ declare v_owner uuid; begin select author_id into v_owner from social.user_stories where id=new.story_id; perform private.dispatch_panalink_notification(v_owner,new.user_id,'STORY_REACTION','STORIES','Reacción a tu historia','A alguien le gustó tu historia',new.story_id,'story','panalink_alerts_v3','NORMAL','story_like:'||new.story_id::text,'{}'::jsonb); return new; end; $fn$;
create or replace function public.notify_on_story_comment() returns trigger language plpgsql security definer set search_path='pg_catalog','public','social','private','social_events','pg_temp' as $fn$ declare v_owner uuid; begin select author_id into v_owner from social.user_stories where id=new.story_id; perform private.dispatch_panalink_notification(v_owner,new.author_id,'STORY_REPLY','STORIES','Respuesta a tu historia',coalesce((select display_name from public.profiles where id=new.author_id),'Alguien')||' respondió tu historia',new.story_id,'story','panalink_alerts_v3','HIGH','story_comment:'||new.story_id::text,jsonb_build_object('comment_id',new.id::text)); return new; end; $fn$;
create or replace function public.notify_on_follow() returns trigger language plpgsql security definer set search_path='pg_catalog','public','private','social_events','pg_temp' as $fn$ begin perform private.dispatch_panalink_notification(new.followed_id,new.follower_id,'PROFILE_FOLLOW','PROFILE','Nuevo seguidor',coalesce((select display_name from public.profiles where id=new.follower_id),'Alguien')||' comenzó a seguirte',new.follower_id,'profile','panalink_alerts_v3','NORMAL','follow:'||new.followed_id::text,jsonb_build_object('follower_id',new.follower_id::text)); return new; end; $fn$;
create or replace function public.notify_followers_on_new_reel() returns trigger language plpgsql security definer set search_path='pg_catalog','public','social','private','social_events','pg_temp' as $fn$ declare r record; v_name text; begin select coalesce(display_name,'Alguien') into v_name from public.profiles where id=new.author_id; for r in select follower_id from social.user_followers where followed_id=new.author_id and follower_id<>new.author_id loop perform private.dispatch_panalink_notification(r.follower_id,new.author_id,'REEL_PUBLISHED','REELS','Nuevo reel de '||v_name,v_name||' publicó un nuevo reel',new.id,'reel','panalink_alerts_v3','NORMAL','new_reel:'||new.id::text,'{}'::jsonb); end loop; return new; end; $fn$;
create or replace function public.notify_followers_on_new_story() returns trigger language plpgsql security definer set search_path='pg_catalog','public','social','private','social_events','pg_temp' as $fn$ declare r record; v_name text; begin select coalesce(display_name,'Alguien') into v_name from public.profiles where id=new.author_id; for r in select follower_id from social.user_followers where followed_id=new.author_id and follower_id<>new.author_id loop perform private.dispatch_panalink_notification(r.follower_id,new.author_id,'STORY_PUBLISHED','STORIES','Nueva historia de '||v_name,v_name||' publicó una nueva historia',new.id,'story','panalink_alerts_v3','NORMAL','new_story:'||new.id::text,'{}'::jsonb); end loop; return new; end; $fn$;
create or replace function public.notify_on_call_hangup() returns trigger language plpgsql security definer set search_path='pg_catalog','public','auth','private','pg_temp' as $fn$ declare v_target uuid; v_name text; begin if new.event_type<>'hangup' then return new; end if; select case when created_by=new.sender_id then coalesce(participant_b,participant_a) else created_by end into v_target from public.call_sessions where id=new.session_id; if v_target is null or v_target=new.sender_id then return new; end if; if not exists(select 1 from public.call_signaling_events e where e.session_id=new.session_id and e.event_type='join' and e.sender_id=v_target) then select coalesce(display_name,'Tu Pana') into v_name from public.profiles where id=new.sender_id; perform private.dispatch_panalink_notification(v_target,new.sender_id,'CALL_MISSED','CALLS','Llamada perdida',v_name||' te llamó y no contestaste',new.session_id,'call','panalink_calls_v3','HIGH','missed_call:'||new.session_id::text,jsonb_build_object('caller_id',new.sender_id::text)); end if; return new; end; $fn$;

drop trigger if exists trg_notify_post_like on public.post_likes;
create trigger trg_notify_post_like after insert on public.post_likes for each row execute function public.notify_on_post_like();
drop trigger if exists trg_notify_post_comment on public.post_comments;
create trigger trg_notify_post_comment after insert on public.post_comments for each row execute function public.notify_on_post_comment();
drop trigger if exists trg_notify_reel_like on social.reel_likes;
create trigger trg_notify_reel_like after insert on social.reel_likes for each row execute function public.notify_on_reel_like();
drop trigger if exists trg_notify_reel_comment on social.reel_comments;
create trigger trg_notify_reel_comment after insert on social.reel_comments for each row execute function public.notify_on_reel_comment();
drop trigger if exists trg_notify_reel_comment_reaction on social.reel_comment_reactions;
create trigger trg_notify_reel_comment_reaction after insert on social.reel_comment_reactions for each row execute function public.notify_on_reel_comment_reaction();
drop trigger if exists trg_notify_story_like on social.story_likes;
create trigger trg_notify_story_like after insert on social.story_likes for each row execute function public.notify_on_story_like();
drop trigger if exists trg_notify_story_comment on social.story_comments;
create trigger trg_notify_story_comment after insert on social.story_comments for each row execute function public.notify_on_story_comment();
drop trigger if exists trg_notify_follow on social.user_followers;
create trigger trg_notify_follow after insert on social.user_followers for each row execute function public.notify_on_follow();
drop trigger if exists trg_notify_followers_new_reel on social.user_reels;
create trigger trg_notify_followers_new_reel after insert on social.user_reels for each row execute function public.notify_followers_on_new_reel();
drop trigger if exists trg_notify_followers_new_story on social.user_stories;
create trigger trg_notify_followers_new_story after insert on social.user_stories for each row execute function public.notify_followers_on_new_story();
drop trigger if exists trg_notify_missed_call on public.call_signaling_events;
create trigger trg_notify_missed_call after insert on public.call_signaling_events for each row execute function public.notify_on_call_hangup();

create index if not exists idx_user_followers_followed_id on social.user_followers(followed_id);
create index if not exists idx_notifications_v2_recipient_created on public.notifications_v2(recipient_id,created_at desc);
create index if not exists idx_call_signaling_events_session_type_sender on public.call_signaling_events(session_id,event_type,sender_id);
