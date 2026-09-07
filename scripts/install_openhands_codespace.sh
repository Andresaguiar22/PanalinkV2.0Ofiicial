#!/usr/bin/env bash
set -euo pipefail

# =============================================
#  OpenHands GRATIS en tu Codespace via GitHub Models
#  Uso: bash scripts/install_openhands_codespace.sh
# =============================================

echo "==> 1/5  Instalando uv..."
if ! command -v uv >/dev/null 2>&1; then
    curl -fsSL https://astral.sh/uv/install.sh | bash
    export PATH="$HOME/.local/bin:$PATH"
fi

echo "==> 2/5  Instalando OpenHands CLI..."
if ! command -v openhands >/dev/null 2>&1; then
    uv tool install openhands --python 3.12
fi

echo "==> 3/5  Obteniendo token de GitHub (para GitHub Models)..."
BASE_URL="https://models.github.ai/v1"

# El token integrado de Codespaces (gh auth token) normalmente NO tiene models:read.
# Solo lo usamos como primer intento; si falla, pedimos un PAT explicito.
try_token() {
    curl -s -o /tmp/gm_models.json -w '%{http_code}' "$BASE_URL/models" -H "Authorization: Bearer $1" -H "Accept: application/json"
}

export GITHUB_TOKEN="$(gh auth token 2>/dev/null || echo "${GITHUB_TOKEN:-}")"
HTTP_CODE="$(try_token "$GITHUB_TOKEN" 2>/dev/null)"

if [ "$HTTP_CODE" != "200" ]; then
    echo "   El token actual no tiene acceso a GitHub Models (HTTP ${HTTP_CODE:-(vacio)})."
    read -rsp "   Pega tu GitHub PAT con permiso 'models:read' (se usara solo localmente): " GITHUB_TOKEN
    echo
    HTTP_CODE="$(try_token "$GITHUB_TOKEN")"
fi

if [ "$HTTP_CODE" = "200" ]; then
    MODEL_ID="$(python3 -c "import json;d=json.load(open('/tmp/gm_models.json));print(next((m['id'] for m in d.get('data',[]) if 'gpt-4o-mini' in m.get('id',''))), 'openai/gpt-4o-mini')" 2>/dev/null)"
    echo "   OK GitHub Models accesible - modelo: ${MODEL_ID}"
else
    MODEL_ID="openai/gpt-4o-mini"
    echo "   ERROR: HTTP ${HTTP_CODE} - no se pudo validar GitHub Models."
    if [ -z "$GITHUB_TOKEN" ]; then exit 1; fi
    echo "   Continuo igualmente (el modelo ${MODEL_ID} puede fallar)..."
fi

export LLM_API_KEY="$GITHUB_TOKEN"
export LLM_BASE_URL="$BASE_URL"
export LLM_MODEL="${LLM_MODEL:-$MODEL_ID}"

echo "==> 5/5  Lanzando OpenHands... (dame la tarea)"
cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
echo "     LLM_MODEL=${LLM_MODEL}"
echo "     LLM_BASE_URL=${LLM_BASE_URL}"
echo

if openhands --llm-model --help &>/dev/null; then
    exec openhands --llm-model "$LLM_MODEL" --llm-api-key "$LLM_API_KEY" --llm-base-url "$LLM_BASE_URL" "$@"
elif openhands --override-with-envs --help &>/dev/null; then
    exec openhands --override-with-envs "$@"
else
    exec openhands "$@"
fi