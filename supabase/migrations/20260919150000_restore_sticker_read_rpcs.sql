-- R2: implementar RPC de lectura de stickers que la app v1.3.49 consume y que
--        nunca se crearon en backend. El schema (stickers, sticker_favorites, recent_stickers,
--        user_stickers) existe en prod con RLS own, las RPC hermanas (favorite_sticker,
--        save_sticker, unfavorite_sticker) marcan las convenciones: SECURITY DEFINER +
--        set search_path + auth.uid() + errores not_authenticated/sticker_not_accessible.
-- Contrato del APK (SupabaseApiService.getSavedStickers/getFavoriteStickers/getRecentStickers):
--        POST sin body, devuelve List<Map> con id/title/url|sticker_url/preview|preview_url/width/height.
--        El parser StickerRepository.parseStickerResultList usa url o sticker_url, preview o
--        preview_url o url. El UI (StickerPanel) solo consume url+preview.
-- Cambios de revision: auth.uid() explicito en las 3 de lectura; sin EXCEPTION WHEN OTHERS en el
--        overload (los errores se propagan, no se ocultan en JSON); parametro sticker_url que
--        coincide con la clave del body de la app; REVOKE PUBLIC + grant solo a authenticated.



create or replace function public.get_favorite_stickers()
returns table(sticker_url text, preview_url text, id uuid, width integer, height integer, title text)
language plpgsql stable security definer set search_path='' as $$
begin
  if auth.uid() is null then
    raise exception 'not_authenticated';
  end if;
  return query
    select s.image_url as sticker_url,
           s.thumbnail_url as preview_url,
           s.id, s.width, s.height,
           s.name as title
    from public.stickers s
         join public.sticker_favorites f on f.sticker_id = s.id
    where f.user_id = auth.uid() and s.deleted_at is null
    order by f.created_at desc;
end;
$$;

create or replace function public.get_recent_stickers()
returns table(sticker_url text, preview_url text, id uuid, width integer, height integer, title text)
language plpgsql stable security definer set search_path='' as $$
begin
  if auth.uid() is null then
    raise exception 'not_authenticated';
  end if;
  return query
    select s.image_url as sticker_url,
           s.thumbnail_url as preview_url,
           s.id, s.width, s.height,
           s.name as title
    from public.stickers s
         join public.recent_stickers r on r.sticker_id = s.id
    where r.user_id = auth.uid() and s.deleted_at is null
    order by r.last_used_at desc, r.created_at desc;
end;
$$;

create or replace function public.get_saved_stickers()
returns table(sticker_url text, preview_url text, id uuid, width integer, height integer, title text)
language plpgsql stable security definer set search_path='' as $$
begin
  if auth.uid() is null then
    raise exception 'not_authenticated';
  end if;
  return query
    select s.image_url as sticker_url,
           s.thumbnail_url as preview_url,
           s.id, s.width, s.height,
           s.name as title
    from public.stickers s
         join public.user_stickers u on u.sticker_id = s.id
    where u.user_id = auth.uid() and s.deleted_at is null
    order by coalesce(u.saved_at, u.created_at) desc;
end;
$$;

create or replace function public.register_sticker_usage(sticker_url text)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  v_uid uuid := auth.uid();
  v_sticker_id uuid;
begin
  if v_uid is null then
    raise exception 'not_authenticated';
  end if;

  select s.id into v_sticker_id
  from public.stickers s
  where trim(s.image_url) = trim(sticker_url)
    and s.deleted_at is null
    and (s.is_official = true or s.visibility = 'public' or s.owner_id = v_uid
       or exists (select 1 from public.user_stickers us where us.user_id = v_uid and us.sticker_id = s.id));

  if v_sticker_id is null then
    raise exception 'sticker_not_accessible';
  end if;

  update public.stickers
   set usage_count = usage_count + 1,
       updated_at = now()
   where id = v_sticker_id;

  insert into public.recent_stickers(user_id, sticker_id, last_used_at, use_count, created_at)
  values ( v_uid, v_sticker_id, now(),1,now())
  on conflict (user_id, sticker_id) do update
    set last_used_at = now(),
        use_count = public.recent_stickers.use_count + 1;

  return jsonb_build_object('ok', true,'action', 'usage_registered','user_id', v_uid,'sticker_id', v_sticker_id);
end;
$$;


revoke execute on function public.get_favorite_stickers() from public;
revoke execute on function public.get_recent_stickers() from public;
revoke execute on function public.get_saved_stickers() from public;
revoke execute on function public.register_sticker_usage(text) from public;
grant execute on function public.get_favorite_stickers() to authenticated;
grant execute on function public.get_recent_stickers() to authenticated;
grant execute on function public.get_saved_stickers() to authenticated;
grant execute on function public.register_sticker_usage(text) to authenticated;
