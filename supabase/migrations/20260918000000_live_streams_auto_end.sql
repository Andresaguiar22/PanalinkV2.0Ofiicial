-- Red telefónica de seguridad contra "lives fantasma".
--
-- Problema: si el host muere (fuerza cierre, caída de red sin `endLive`, bug en el
-- APK), el row live_streams queda con status='LIVE' y ended_at=null para siempre
-- (se reportaron varios en producción). El feed los sigue mostrando y los
-- espectadores ven una sala inexistente.
--
-- Solución en dos capas:
--   1) Heartbeat: el host hace PATCH a live_streams (ya llamaba a
--      live_set_viewer_count), pero ahora además toca `last_seen_at` cada ≤30s.
--   2) pg_cron: cada 10 segundos marca ENDED cualquier LIVE con
--      last_seen_at < now() - 60s (3 periodos de heartbeat perdidos).

alter table public.live_streams
    add column if not exists last_seen_at timestamptz default now();

create or replace function public.live_heartbeat(p_stream_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    update public.live_streams
    set last_seen_at = now()
    where id = p_stream_id
      and host_id = auth.uid()
      and status = 'LIVE';
end;
$$;

-- Acceso desde la app: RPC con RLS (authenticated y host de la sala).
revoke execute on function public.live_heartbeat(uuid) from public;
grant execute on function public.live_heartbeat(uuid) to authenticated;

-- Reintento perezoso y seguro (idempotente).
create or replace function public.live_auto_end_stale()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    update public.live_streams
    set status = 'ENDED',
        ended_at = coalesce(ended_at, now())
    where status = 'LIVE'
      and (last_seen_at is null or last_seen_at < now() - interval '60 seconds');
end;
$$;

revoke execute on function public.live_auto_end_stale() from public;
grant execute on function public.live_auto_end_stale() to service_role;

-- Programación del cron: cada 10 segundos, marca ENDED los LIVE con heartbeat
-- vencido. Si el valor remoto no cambia, la re-cron no es un problema.
select cron.schedule('live-auto-end-stale', '*/10 * * * * *', $$select public.live_auto_end_stale()$$)
where not exists (
    select 1 from cron.job where jobname = 'live-auto-end-stale'
);