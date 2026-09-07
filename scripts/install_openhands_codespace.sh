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
HTTP_CODE="$(curl -s -o /tmp/gm_models.json -w '%{http_code}' "$BASE_URL/models" -H "Authorization: Bearer ${GITHUB_TOKEN:-}" -H "Accept: application/json" 2>/dev/null)"

if [ "$HTTP_CODE" = "410" ]; then
    echo "   AVISO: GitHub Models esta en 'scheduled retirement brownout' (HTTP 410) — el catalogo"
    echo "           publico gratis de GitHub Models ha sido retirado. Usamos Gemini (gratis) en su lugar.."
    PROVIDER="gemini"
elif [ "$HTTP_CODE" = "200" ]; then
    echo "   OK GitHub Models accesible (usando openai/gpt-4o-mini)..."
    PROVIDER="github"
else
    echo "   AVISO: GitHub Models responde HTTP ${HTTP_CODE:-?} — usamos Gemini (gratis) en su lugar.."
    PROVIDER="gemini"
fi

if [ "$PROVIDER" = "github" ]; then
    export GITHUB_TOKEN="${GITHUB_TOKEN:-$(gh auth token 2>/dev/null || true)}"
    export LLM_API_KEY="$GITHUB_TOKEN"
    export LLM_BASE_URL="$BASE_URL"
    export LLM_MODEL="${LLM_MODEL:-openai/gpt-4o-mini}"
else
    echo
    if [ -z "${GEMINI_API_KEY:-}" ]; then
        echo "   Necesitamos tu Gemini API key (GRATIS, sin tarjeta):"
        echo "    1. Abre https://aistudio.google.com/apikey (login con Google)"
        echo "    2. Create API key → copiala"
        read -rsp "    3. Pegala aqui: " GEMINI_API_KEY
        echo
    fi
    # IMPORTANTE: LiteLLM/OpenHands usa el proveedor nativo 'gemini/'
    # (prefijo 'gemini/', NO 'google/', y SIN base URL custom).
    unset LLM_BASE_URL
    export GEMINI_API_KEY
    export LLM_API_KEY="$GEMINI_API_KEY"
    export LLM_MODEL="${LLM_MODEL:-gemini/gemini-2.5-flash}"
fi

echo "==> 4/5  Verificando el LLM..."
if [ "$PROVIDER" = "github" ]; then
    CODE="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "${LLM_BASE_URL}models" -H "Authorization: Bearer $LLM_API_KEY" -H "Accept: application/json" 2>/dev/null)"
    echo "   GET ${LLM_BASE_URL}models -> HTTP ${CODE:-?}"
else
    # Gemini nativo: el test real es un chat/completions minimo, no /models.
    CODE="$(curl -s -o /tmp/gemini_chat.json -w '%{http_code}' --max-time 20 \
        -X POST https://generativelanguage.googleapis.com/v1beta/openai/chat/completions \
        -H "Authorization: Bearer $LLM_API_KEY" -H "Content-Type: application/json" \
        -d "{\"model\":\"gemini-2.5-flash\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}],\"max_tokens\":5}" 2>/dev/null)"
    if [ "$CODE" = "200" ]; then
        echo "   OK Gemini accesible (clave valida)"
    else
        echo "   AVISO: test Gemini -> HTTP ${CODE:-?} (puede ser normal si la clave no tiene acceso al modelo; OpenHands probara igualmente)"
    fi
fi

echo "==> 5/5  Lanzando OpenHands... (dame la tarea)"
cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
echo "     LLM_MODEL=${LLM_MODEL}"
echo "     LLM_BASE_URL=${LLM_BASE_URL}"
echo

export LLM_API_KEY="$LLM_API_KEY"
if [ -n "${LLM_BASE_URL:-}" ]; then
    export LLM_BASE_URL="$LLM_BASE_URL"
else
    unset LLM_BASE_URL 2>/dev/null || true
fi
export LLM_MODEL="$LLM_MODEL"

exec openhands --override-with-envs "$@" 2>/dev/null || exec openhands "$@"