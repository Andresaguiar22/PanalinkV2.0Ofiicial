# Handoff: aplicar la migración de retención (mantener la BD flaca en Free)

Rama: `kilo/clean-ui-ios` · Repo: `/workspace/project/PanalinkV2.0Ofiicial`

## Qué hay que aplicar

```
supabase/migrations/20260925100000_retention_purge.sql
```

Validadas en PostgreSQL 15 local: purga de filas viejas, conservación de
recientes, borrado por lotes (varias iteraciones) y permisos
(`authenticated` -> permission denied; `service_role` -> ok). Todo verde.

## Qué hace

Crea un purgador genérico y su cron diario (04:00 UTC):

| Objeto | Detalle |
|---|---|
| `public.purge_old_rows(tabla, columna_ts, retención, lote)` | borra por lotes con `ctid ... limit`; valida tabla/columna contra catálogo |
| `public.retention_purge()` | aplica las ventanas de retención por tabla; devuelve un jsonb con las filas borradas |
| cron `retention_purge_daily` | `0 4 * * *` -> `select public.retention_purge()` |
| índices `idx_<tabla>_created_at` | solo se crean si la tabla existe |

Ventanas de retención:
- `presence_events` 2 días · `call_signaling_events` 7 días
- `notification_events` 14 días · `voice_room_entrance_events` 7 días
- `live_comments` 30 días
- `live_gift_events` 180 días · `premium_audit_log` 180 días

El ledger real de dinero (`wallet_transactions`) y los mensajes de chat
(`thread_messages`) **no se tocan**: la retención es solo de telemetría.

## Requisito: PAT válido de Management API

El `SUPABASE_ACCESS_TOKEN` de este entorno es inválido (401). Usar uno nuevo:
dashboard -> **Account -> Access Tokens -> Generate new token**.

Comprobar (debe dar 200, no 401):
```bash
curl -s -o /dev/null -w "HTTP %{http_code}\n" \
  "https://api.supabase.com/v1/projects" \
  -H "Authorization: Bearer ${SUPABASE_ACCESS_TOKEN}"
```

## Paso 1 — Aplicar

```bash
cd /workspace/project/PanalinkV2.0Ofiicial
SUPABASE_ACCESS_TOKEN=sbp_... SUPABASE_PROJECT_ID=tivqjfgjdxgzicrridaz \
  bash scripts/apply_migration.sh supabase/migrations/20260925100000_retention_purge.sql
```

Salida esperada: `>>> OK (HTTP 200)`.

## Paso 2 — Verificar

```bash
# Debe devolver jsonb con las tablas purgadas (o errores por tabla ausente):
curl -s -X POST "https://tivqjfgjdxgzicrridaz.supabase.co/rest/v1/rpc/retention_purge" \
  -H "apikey: ${SUPABASE_SERVICE_ROLE_KEY}" \
  -H "Authorization: Bearer ${SUPABASE_SERVICE_ROLE_KEY}" \
  -H "Content-Type: application/json" -d '{}' -w "\nHTTP %{http_code}\n"
```
- `200` con jsonb = funciona.
- `404 PGRST202` = no se aplicó.
- `401/403` con la anon key = correcto (solo `service_role` puede).

## Paso 3 — Comprobar que el cron quedó

```sql
select jobname, schedule from cron.job where jobname = 'retention_purge_daily';
```

## Notas

- La migración es **idempotente** (`create ... if not exists`, `create or
  replace`, y el cron se desagenda antes de re-agendar). Se puede re-aplicar.
- Tablas que no existan en la BD se omiten sin romper la purga.
- No toca RLS de datos de usuario ni políticas existentes.
