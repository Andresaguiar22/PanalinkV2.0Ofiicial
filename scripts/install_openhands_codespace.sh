#!/usr/bin/env bash
set -euo pipefail

# =============================================
#  OpenHands GRATIS en tu Codespace
#  Uso: bash scripts/install_openhands_codespace.sh [opciones openhands]
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

echo "==> 3/5  Configurando LLM (gratis)..."
BASE_URL="https://models.github.ai/v1"
GH_CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$BASE_URL/models" -H "Authorization: Bearer ${GITHUB_TOKEN:-}" -H "Accept: application/json" 2>/dev/null)"

if [ "$GH_CODE" = "410" ]; then
    echo "   AVISO: GitHub Models esta en 'scheduled retirement brownout' (HTTP 410) — retirado. Usamos otra via gratis.."
elif [ "$GH_CODE" != "200" ]; then
    echo "   AVISO: GitHub Models responde HTTP ${GH_CODE:-?} — usamos otra via gratis.."
fi

PROVIDER=""
# 1) OpenRouter (gratis: modelo :free) si la API key esta disponible
if [ -z "$PROVIDER" ] && [ -n "${OPENROUTER_API_KEY:-}" ]; then
    TEST="$(curl -s -o /tmp/or_test.json -w '%{http_code}' --max-time 20 https://openrouter.ai/api/v1/chat/completions -H "Authorization: Bearer $OPENROUTER_API_KEY" -H "Content-Type: application/json" -d '{"model":"minimax/minimax-m2.7:free","messages":[{"role":"user","content":"ping"}],"max_tokens":5}')"
    if [ "$TEST" = "200" ]; then
        echo "   OK OpenRouter + minimax/minimax-m2.7:free (gratis)"
        PROVIDER="openrouter"
    else
        echo "   AVISO: OpenRouter responde HTTP ${TEST:-?} — probamos Gemini.."
    fi
fi

# 2) Gemini (gratis) si no hay OpenRouter
if [ -z "$PROVIDER" ]; then
    echo
    if [ -z "${GEMINI_API_KEY:-}" ]; then
        echo "   Necesitamos tu Gemini API key (GRATIS, sin tarjeta):"
        echo "    1. Abre https://aistudio.google.com/apikey (login con Google)"
        echo "    2. Create API key → copiala"
        read -rsp "    3. Pegala aqui: " GEMINI_API_KEY
        echo
    fi
    # IMPORTANTE: LiteLLM/OpenHands usa el proveedor nativo 'gemini/'
    unset LLM_BASE_URL
    export GEMINI_API_KEY
    export LLM_API_KEY="$GEMINI_API_KEY"
    export LLM_MODEL="${LLM_MODEL:-gemini/gemini-2.5-flash}"
    PROVIDER="gemini"
fi

if [ "$PROVIDER" = "openrouter" ]; then
    export LLM_API_KEY="$OPENROUTER_API_KEY"
    export LLM_BASE_URL="https://openrouter.ai/api/v1"
    export LLM_MODEL="${LLM_MODEL:-openrouter/minimax/minimax-m2.7:free}"
fi

echo "==> 4/5  Verificando el LLM..."
TEST_MODEL="$(echo "$LLM_MODEL" | sed 's|.*/||')"
CODE="$(curl -s -o /tmp/llm_test.json -w '%{http_code}' --max-time 20 \
    -X POST "${LLM_BASE_URL:-https://generativelanguage.googleapis.com/v1beta/openai}/chat/completions" \
    -H "Authorization: Bearer $LLM_API_KEY" -H "Content-Type: application/json" \
    -d "{\"model\":\"$TEST_MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":5}" 2>/dev/null)"
if [ "$CODE" = "200" ]; then
    echo "   OK LLM accesible (clave valida)"
else
    echo "   AVISO: test LLM -> HTTP ${CODE:-?} (OpenHands probara igualmente)"
fi

echo "==> 5/5  Lanzando OpenHands... (dame la tarea)"
cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
echo "     LLM_MODEL=${LLM_MODEL}"
echo "     LLM_BASE_URL=${LLM_BASE_URL:-<nativo>}"
echo

# Limpieza critica de telemetria: si LMNR_*/OTEL_* estan puestas (comun
# en Codespaces/entornos de agente), el decorador de observabilidad de
# OpenHands crashea con TypeError (rollout_entrypoint) y la TUI se cuelga.

for _v in $(env | grep -oE "^(LMNR|OTEL)[A-Z_]*" || true); do
    unset "$_v" 2>/dev/null || true
done
unset LMNR_PROJECT_API_KEY LMNR_BASE_URL LMNR_FORCE_HTTP LMNR_HTTP_PORT LMNR_GRPC_PORT 2>/dev/null || true

export LLM_API_KEY="$LLM_API_KEY"
if [ -n "${LLM_BASE_URL:-}" ]; then
    export LLM_BASE_URL="$LLM_BASE_URL"
else
    unset LLM_BASE_URL 2>/dev/null || true
fi
export LLM_MODEL="$LLM_MODEL"

exec openhands --override-with-envs "$@" 2>/dev/null || exec openhands "$@"