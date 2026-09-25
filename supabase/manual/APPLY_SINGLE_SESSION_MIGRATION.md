# Handoff: aplicar migración de "sesión única por cuenta"

Rama: `kilo/clean-ui-ios` · Repo: `/workspace/project/PanalinkV2.0Ofiicial`

## Qué hay que aplicar

Un solo fichero, ya validado en PostgreSQL 15 local (RLS, aislamiento entre
usuarios, rechazo de anónimos y trigger de respaldo: todo verde):

```
supabase/migrations/20260925000000_single_active_session.sql
```

Crea:
- tabla `public.user_devices` (+ índice + RLS: cada usuario solo ve sus filas)
- RPC `public.register_device(p_device_id text, p_device_name text)`
- RPC `public.get_other_active_devices(p_current_device_id text) -> jsonb`
- trigger `trg_touch_device_on_session` sobre `auth.sessions` (respaldo)
- añade `user_devices` a la publicación `supabase_realtime`

## Requisito imprescindible: un PAT válido

El `SUPABASE_ACCESS_TOKEN` de ESTE entorno es **inválido** (Supabase responde
`401 Unauthorized` tanto a la API como a la CLI oficial). Hay que usar uno
nuevo: Supabase dashboard -> **Account -> Access Tokens -> Generate new token**.

Exportar:
```bash
export SUPABASE_ACCESS_TOKEN=sbp_...        # PAT con permiso Management API
export SUPABASE_PROJECT_ID=<project-ref>
```

Comprobar que funciona (debe dar HTTP 200, no 401):
```bash
curl -s -o /dev/null -w "HTTP %{http_code}\n" \
  "https://api.supabase.com/v1/projects" \
  -H "Authorization: Bearer ${SUPABASE_ACCESS_TOKEN}"
```

## Paso 1 — Aplicar la migración

```bash
cd /workspace/project/PanalinkV2.0Ofiicial
SUPABASE_ACCESS_TOKEN=sbp_... SUPABASE_PROJECT_ID=<ref> \
  bash scripts/apply_migration.sh
```

Salida esperada: `>>> OK (HTTP 200)`. Si da 401, el token no sirve.

Alternativa con curl directo:
```bash
python3 -c "import json; json.dump({'query': open('supabase/migrations/20260925000000_single_active_session.sql').read()}, open('/tmp/mig.json','w'))"
curl -s -X POST "https://api.supabase.com/v1/projects/${SUPABASE_PROJECT_ID}/database/query" \
  -H "Authorization: Bearer ${SUPABASE_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  --data-binary @/tmp/mig.json -w "\nHTTP %{http_code}\n"
```

## Paso 2 — Limitar a 1 sesión por usuario (NO es SQL)

Esto vive en la config de GoTrue, no en el esquema. En el dashboard:

Authentication -> **Sessions** -> **Max sessions per user = 1**

Con eso, al iniciar sesión en un segundo dispositivo GoTrue revoca la sesión
antigua; la app recibe `401 invalid_grant` al refrescar y muestra el aviso
"Tu sesión se cerró porque iniciaste en otro dispositivo".

> Por API se intentaría con `PATCH /v1/projects/{ref}/config/auth`, pero hay
> que leer primero `GET /v1/projects/{ref}/config/auth` para confirmar el
> nombre exacto del campo en esa versión antes de escribirlo. El dashboard es
> la vía segura.

## Paso 3 — Verificar

La tabla debe existir (200 + `[]`, NO 404):
```bash
curl -s -w "\nHTTP %{http_code}\n" \
  "https://${SUPABASE_PROJECT_ID}.supabase.co/rest/v1/user_devices?select=*&limit=1" \
  -H "apikey: ${SUPABASE_SERVICE_ROLE_KEY}" \
  -H "Authorization: Bearer ${SUPABASE_SERVICE_ROLE_KEY}"
```

El RPC debe existir (401 `not_authenticated` sin JWT de usuario, NO 404):
```bash
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST \
  "https://${SUPABASE_PROJECT_ID}.supabase.co/rest/v1/rpc/register_device" \
  -H "apikey: ${SUPABASE_SERVICE_ROLE_KEY}" \
  -H "Authorization: Bearer ${SUPABASE_SERVICE_ROLE_KEY}" \
  -H "Content-Type: application/json" -d '{"p_device_id":"probe","p_device_name":"probe"}'
```

- `200/201` en el POST de la API de management = migración aplicada.
- `404 PGRST202` en el RPC = NO se aplicó.
- `401 not_authenticated` = el RPC existe y funciona (correcto).

## Qué NO tocar

- No modificar `scripts/toolchain_env.sh` (debe quedar portable).
- No commitear `app/google-services.json` ni secretos.
- Sin bytes invisibles: correr `bash scripts/sanitize_invisible.sh` tras editar.

## Contexto de app (ya implementado y compilando)

`SessionManager` emite `SESSION_REVOKED` con `401 invalid_grant`; `MainActivity`
muestra el diálogo. `AuthManager` llama `register_device` +
`get_other_active_devices` tras el login (best-effort: si el RPC no existe,
404 y sigue sin romper nada). `DeviceInfo.kt` da el id/nombre del dispositivo.
