-- Feed like/comment root-cause repair.
-- bump_relationship() is intentionally private (EXECUTE revoked from
-- public/anon/authenticated in 20260816213500). The feed AFTER INSERT
-- triggers ran as SECURITY INVOKER, so every post like/comment rolled back
-- with 42501 (permission denied for function bump_relationship) and the
-- client retried forever in silence. Same fix pattern as 20260816214500
-- for chat: run the triggers as SECURITY DEFINER.

create or replace function public.trg_post_likes_after_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_post_owner uuid;
begin
  select user_id into v_post_owner from public.posts where id = new.post_id;
  perform public.bump_relationship(
    new.user_id,
    v_post_owner,
    0,
    0,
    1,
    1,
    null
  );
  return new;
end;
$$;

create or replace function public.trg_post_comments_after_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_post_owner uuid;
begin
  select user_id into v_post_owner from public.posts where id = new.post_id;
  perform public.bump_relationship(
    new.user_id,
    v_post_owner,
    0,
    1,
    0,
    1,
    null
  );
  return new;
end;
$$;

revoke execute on function public.trg_post_likes_after_insert() from public, anon, authenticated;
revoke execute on function public.trg_post_comments_after_insert() from public, anon, authenticated;
