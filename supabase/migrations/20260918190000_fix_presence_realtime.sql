-- Fix del mecanismo de presencia en tiempo real.
--
-- CAUSA RAIZ (confirmada en prod vía probes + Management API)：
--   1) La función `public.presence_realtime_broadcast_trigger()` referencia
--      `NEW.computed_status`, una columna que NO existe en `public.user_presence`
--      (esa columna vive en la VISTA `user_presence_status`)。Resultado: TODA
--      escritura a `user_presence` (y a `presence_events`) FAILs con
--      HTTP 400 "record \"new\" has no field \"computed_status\"" → la tabla
--      quedó congelada desde el 2026-08-06 y el punto verde murió.
--   2) La app (commits `8f35761` y posteriores) dejó de usar `broadcastPresence`
--      y pasó al "presence nativo" de Phoenix, que **el servidor NO implementa**
--      (verificado empíricamente: phx_join ok, pero jamás llegan `presence_state`
--      /`presence_diff`, incluso con JWT autenticado)。
--
-- Solución backend:
-- la función trigger ahora NO referencia columnas individuales： usa
-- `to_jsonb(new)`/`to_jsonb(old)` y pasa la fila `new`/`old` (record) a
-- `realtime.broadcast_changes`, exactamente el patrón de
-- `thread_messages_realtime_broadcast` (el flujo DM que SÍ funciona)。
-- Así el canal broadcast `presence:{uid}` vuelve a ser viable y las escrituras
-- de persistencia dejan de romperse。



create or replace function public.presence_realtime_broadcast_trigger()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
declare
    uid uuid;
begin
    uid := coalesce(new.user_id, old.user_id);
    perform realtime.broadcast_changes(
        'presence:' || uid::text,
        tg_op,
        tg_op,
        tg_table_name,
        tg_table_schema,
        new,
        old
    );
    return coalesce(new, old);
end;
$function$;