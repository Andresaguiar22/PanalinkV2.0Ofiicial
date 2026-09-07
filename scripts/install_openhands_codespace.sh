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

echo "==> 3/5  Configurando GitHub Models (gratis)..."
export GITHUB_TOKEN="$(gh auth token 2>/dev/null || echo "${GITHUB_TOKEN:-}")"
if [ -z "$GITHUB_TOKEN" ]; then
    read -rsp "Pega tu GitHub PAT (con models:read): " GITHUB_TOKEN
    echo
fi

export LLM_API_KEY="$GITHUB_TOKEN"
export LLM_BASE_URL="https://models.github.ai/v1"
export LLM_MODEL="${LLM_MODEL:-openai/gpt-4o-mini}"

echo "==> 4/5  Verificando modelo..."
CODE="$(curl -s -o /dev/null -w '%{http_code}' https://models.github.ai/v1/models -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/json")"
if [ "$CODE" = "200" ]; then
    echo "   OK GitHub Models accesible"
else
    echo "   AVISO HTTP $CODE (el PAT necesita models:read; sigo igualmente)"
fi

echo "==> 5/5  Lanzando OpenHands... (dame la tarea)"
cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
if openhands --override-with-envs --help &>/dev/null; then
    exec openhands --override-with-envs "$@"
else
    exec openhands "$@"
fi