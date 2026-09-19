-- R1: restaurar RPC de voice room que nunca se aplicaron en prod
-- Origen: 20260907200000_voice_rooms_settings.sql (commit de5cb8a, main)
-- Motivo: el APK v1.3.49 las invoca, el schema existe en prod, la migracion
--        historica NO quedo registrada en schema_migrations, las 4 funciones no existen.
-- La replica preserva SECURITY DEFINER + set search_path + checks owner/admin de la original.


-- Panalink Voice Rooms: room settings management for owners (edit, delete, admins, bans).
-- Owner-only via security definer functions, matching create_voice_room validation conventions.

create or replace function public.update_voice_room_settings(
    p_room_id uuid,
    p_name text default null,
    p_description text default null,
    p_cover_url text default null,
    p_category text default null,
    p_visibility text default null,
    p_is_locked boolean default null
) returns setof public.voice_rooms language plpgsql security definer set search_path='' as $$
declare v public.voice_rooms%rowtype;
begin
    if (select auth.uid()) is null then raise exception 'NOT_AUTHENTICATED'; end if;
    if p_name is not null and length(trim(coalesce(p_name,''))) not between 2 and 80 then raise exception 'INVALID_ROOM_NAME'; end if;
    if p_description is not null and length(coalesce(p_description,''))>280 then raise exception 'INVALID_ROOM_DESCRIPTION'; end if;
    if p_visibility is not null and p_visibility not in('public','private') then raise exception 'INVALID_ROOM_VISIBILITY'; end if;
    if p_category is not null and p_category not in('general','chat','meeting','work','dating','friends','music','gaming') then raise exception 'INVALID_ROOM_CATEGORY'; end if;
    update public.voice_rooms set
        name = coalesce(trim(p_name), name),
        description = coalesce(trim(coalesce(p_description,'')), description),
        cover_url = case when p_cover_url is not null then nullif(trim(coalesce(p_cover_url,'')),'') else cover_url end,
        category = coalesce(p_category, category),
        visibility = coalesce(p_visibility, visibility),
        is_locked = coalesce(p_is_locked, is_locked),
        updated_at = now()
    where id = p_room_id and owner_id = (select auth.uid()) returning * into v;
    if not found then raise exception 'ROOM_NOT_FOUND_OR_NOT_OWNER'; end if;
    return next v;
end;
$$;

create or replace function public.delete_voice_room(p_room_id uuid) returns void language plpgsql security definer set search_path='' as $$
begin
    if (select auth.uid()) is null then raise exception 'NOT_AUTHENTICATED'; end if;
    delete from public.voice_rooms where id = p_room_id and owner_id = (select auth.uid());
    if not found then raise exception 'ROOM_NOT_FOUND_OR_NOT_OWNER'; end if;
end;
$$;

create or replace function public.get_voice_room_banned(p_room_id uuid) returns table(
    user_id uuid,
    display_name text,
    avatar_url text,
    reason text,
    banned_at timestamptz
) language sql stable security definer set search_path='' as $$
select b.user_id, coalesce(p.display_name,''), p.avatar_url, b.reason, b.created_at
from public.voice_room_bans b
left join public.public_profiles p on p.id = b.user_id
where b.room_id = p_room_id
order by b.created_at desc;
$$;

create or replace function public.remove_voice_room_ban(p_room_id uuid,p_user_id uuid) returns void language plpgsql security definer set search_path='' as $$
begin
    if not public.voice_room_is_admin(p_room_id) then raise exception 'NOT_ROOM_ADMIN'; end if;
    delete from public.voice_room_bans where room_id = p_room_id and user_id = p_user_id;
    if not found then raise exception 'BAN_NOT_FOUND'; end if;
end;
$$;

grant execute on function public.update_voice_room_settings(uuid, text, text, text, text, text, boolean) to authenticated;
grant execute on function public.delete_voice_room(uuid) to authenticated;
grant execute on function public.get_voice_room_banned(uuid) to authenticated;
grant execute on function public.remove_voice_room_ban(uuid, uuid) to authenticated;
