-- =====================================================================
-- Retención y limpieza automática (PanaLink) - plan Free de Supabase
-- =====================================================================
-- Objetivo: que la base de datos NO crezca indefinidamente. Las tablas de
-- telemetría/eventos (presencia, señalización, notificaciones, comentarios
-- de live, regalos, auditoría) crecen para siempre si nadie las purga.
--
-- Estrategia: un purgador GENÉRICO que valida tabla y columna contra
-- information_schema (nada de SQL dinámico a ciegas) y borra por lotes con
-- `ctid ... limit` para no tomar locks largos ni inflar WAL de golpe.
--
-- Se agenda con pg_cron una vez al día (04:00 UTC, fuera de horas pico).
-- Todo SECURITY DEFINER + search_path='' ; ejecutable SOLO por service_role
-- (nunca por authenticated/anon).
-- =====================================================================

-- ---------------------------------------------------------------------
-- Purga genérica por lotes.
--   p_table      : tabla a purgar (debe existir en public).
--   p_ts_column  : columna de fecha (debe existir y ser timestamp/timestamptz).
--   p_retention  : intervalo de retención (ej. interval '30 days').
--   p_batch      : filas por iteración (evita locks largos).
-- Devuelve el total de filas borradas.
-- ---------------------------------------------------------------------
create or replace function public.purge_old_rows(
    p_table text,
    p_ts_column text,
    p_retention interval,
    p_batch int default 5000
)
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_total bigint := 0;
    v_deleted bigint;
    v_is_ts boolean;
begin
    if p_batch is null or p_batch < 1 then
        p_batch := 5000;
    end if;

    -- Validación dura: la tabla debe existir en public.
    if not exists (
        select 1 from pg_catalog.pg_class c
        join pg_catalog.pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relname = p_table and c.relkind = 'r'
    ) then
        raise exception 'tabla inexistente: public.%', p_table;
    end if;

    -- Validación dura: la columna debe existir y ser de fecha.
    select (data_type in ('timestamp with time zone', 'timestamp without time zone'))
    into v_is_ts
    from information_schema.columns
    where table_schema = 'public' and table_name = p_table and column_name = p_ts_column;

    if v_is_ts is null then
        raise exception 'columna inexistente: public.%.%', p_table, p_ts_column;
    end if;
    if not v_is_ts then
        raise exception 'la columna %.% no es de tipo fecha', p_table, p_ts_column;
    end if;

    loop
        execute format(
            'delete from public.%I where ctid in (
                 select ctid from public.%I where %I < now() - $1 limit $2
             )',
            p_table, p_table, p_ts_column
        ) using p_retention, p_batch;
        get diagnostics v_deleted = row_count;
        v_total := v_total + v_deleted;
        exit when v_deleted < p_batch;
    end loop;

    return v_total;
end;
$$;

revoke all on function public.purge_old_rows(text, text, interval, int) from public, anon, authenticated;

-- ---------------------------------------------------------------------
-- Índices de apoyo: sin índice, el DELETE escanea toda la tabla cada vez.
-- Se crean solo si la tabla existe (la migración debe ser segura de aplicar
-- en cualquier entorno).
-- ---------------------------------------------------------------------
do $$
declare
    r record;
begin
    for r in
        select * from (values
            ('presence_events',            'idx_presence_events_created_at'),
            ('call_signaling_events',      'idx_call_signaling_events_created_at'),
            ('notification_events',        'idx_notification_events_created_at'),
            ('live_comments',              'idx_live_comments_created_at'),
            ('live_gift_events',           'idx_live_gift_events_created_at'),
            ('premium_audit_log',          'idx_premium_audit_log_created_at'),
            ('voice_room_entrance_events', 'idx_voice_room_entrance_events_created_at')
        ) as t(tbl, idx)
    loop
        if exists (
            select 1 from pg_catalog.pg_class c
            join pg_catalog.pg_namespace n on n.oid = c.relnamespace
            where n.nspname = 'public' and c.relname = r.tbl and c.relkind = 'r'
        ) then
            execute format('create index if not exists %I on public.%I (created_at)', r.idx, r.tbl);
        end if;
    end loop;
end $$;

-- ---------------------------------------------------------------------
-- Driver: purga cada tabla con su ventana de retención.
-- Las tablas que aún no existan en esta BD se omiten en silencio, así la
-- migración es segura de aplicar en cualquier entorno.
-- ---------------------------------------------------------------------
create or replace function public.retention_purge()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_result jsonb := '{}'::jsonb;
    v_n bigint;
    r record;
begin
    for r in
        select * from (values
            -- Telemetría de alta frecuencia: se puede tirar rápido.
            ('presence_events',            'created_at', interval '2 days'),
            ('call_signaling_events',      'created_at', interval '7 days'),
            ('notification_events',        'created_at', interval '14 days'),
            ('voice_room_entrance_events', 'created_at', interval '7 days'),
            -- Contenido visible: se conserva más.
            ('live_comments',              'created_at', interval '30 days'),
            -- Afecta a monedero/auditoría: ventana larga (el ledger real,
            -- wallet_transactions, NO se toca nunca).
            ('live_gift_events',           'created_at', interval '180 days'),
            ('premium_audit_log',          'created_at', interval '180 days')
        ) as t(tbl, col, retention)
    loop
        begin
            v_n := public.purge_old_rows(r.tbl, r.col, r.retention);
            v_result := v_result || jsonb_build_object(r.tbl, v_n);
        exception when others then
            -- Tabla ausente o esquema distinto: no romper el resto de la purga.
            v_result := v_result || jsonb_build_object(r.tbl, jsonb_build_object('error', sqlerrm));
        end;
    end loop;

    return v_result;
end;
$$;

revoke all on function public.retention_purge() from public, anon, authenticated;
grant execute on function public.retention_purge() to service_role;

-- ---------------------------------------------------------------------
-- Cron: una vez al día a las 04:00 UTC.
-- Defensivo: si pg_cron no está disponible en el entorno, la migración
-- no falla (el RPC se puede invocar manualmente o agendar después).
-- ---------------------------------------------------------------------
do $$
begin
    begin
        perform cron.unschedule('retention_purge_daily');
    exception when others then null;
    end;
    begin
        perform cron.schedule(
            'retention_purge_daily',
            '0 4 * * *',
            $cron$ select public.retention_purge(); $cron$
        );
    exception when others then
        raise notice 'pg_cron no disponible; agenda retention_purge() manualmente: %', sqlerrm;
    end;
end $$;
