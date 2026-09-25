#!/usr/bin/env bash
# Aplica la migración de sesión única por cuenta en Supabase (producción).
#
# Uso:
#   SUPABASE_ACCESS_TOKEN=sbp_xxx SUPABASE_PROJECT_ID=abcdefgh \
#       bash scripts/apply_single_session_migration.sh
#
# El token debe ser un Personal Access Token con permiso de Management API
# (dashboard -> Account -> Access Tokens). Si el token es inválido, la API
# responde 401 y el script lo reporta sin tocar nada.
#
# Recordatorio: además de esta migración hay que activar en la dashboard
#   Authentication -> Sessions -> "Max sessions per user" = 1
# para que la sesión vieja se revoque de verdad al iniciar en otro dispositivo.

set -uo pipefail

MIGRATION="${1:-supabase/migrations/20260925000000_single_active_session.sql}"

if [ ! -f "$MIGRATION" ]; then
    echo "ERROR: no existe la migración: $MIGRATION" >&2
    exit 1
fi
if [ -z "${SUPABASE_ACCESS_TOKEN:-}" ]; then
    echo "ERROR: falta SUPABASE_ACCESS_TOKEN (PAT de la Management API)." >&2
    exit 1
fi
if [ -z "${SUPABASE_PROJECT_ID:-}" ]; then
    echo "ERROR: falta SUPABASE_PROJECT_ID." >&2
    exit 1
fi

payload="$(mktemp)"
trap 'rm -f "$payload"' EXIT

python3 - "$MIGRATION" "$payload" <<'PY'
import json, sys
sql = open(sys.argv[1], encoding="utf-8").read()
json.dump({"query": sql}, open(sys.argv[2], "w", encoding="utf-8"))
PY

echo ">>> Aplicando $MIGRATION al proyecto $SUPABASE_PROJECT_ID ..."
code="$(curl -s -o /tmp/apply_migration_resp.json -w '%{http_code}' \
    -X POST "https://api.supabase.com/v1/projects/${SUPABASE_PROJECT_ID}/database/query" \
    -H "Authorization: Bearer ${SUPABASE_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    --data-binary "@${payload}")"

if [ "$code" = "200" ] || [ "$code" = "201" ]; then
    echo ">>> OK (HTTP $code). Migración aplicada."
    head -c 400 /tmp/apply_migration_resp.json; echo
else
    echo ">>> FALLO (HTTP $code). Respuesta:" >&2
    head -c 600 /tmp/apply_migration_resp.json >&2; echo >&2
    echo ">>> Revisa que el SUPABASE_ACCESS_TOKEN sea válido y tenga permisos." >&2
    exit 2
fi
