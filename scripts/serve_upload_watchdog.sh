#!/usr/bin/env bash
# Supervisor del servidor de subida de capturas (upload_server.py): lo relanza si
# el proceso muere. Version equivalente a serve_apk_watchdog.sh pero para el
# puerto de capturas (12000).
#
# Uso: bash scripts/serve_upload_watchdog.sh [puerto]
set -u

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${1:-12000}"
UPLOADS_DIR="$REPO/.toolchain/uploads"
LOG="/tmp/upload_server_${PORT}.log"

mkdir -p "$UPLOADS_DIR"

echo "watchdog: puerto=$PORT uploads=$UPLOADS_DIR log=$LOG" >> "$LOG"

while true; do
    if ! ss -tln 2>/dev/null | grep -q ":${PORT} "; then
        echo "$(date '+%F %T') relanzando upload_server en $PORT" >> "$LOG"
        python3 "$REPO/scripts/upload_server.py" "$PORT" "$UPLOADS_DIR" >> "$LOG" 2>&1
        echo "$(date '+%F %T') upload_server termino; reintento en 3s" >> "$LOG"
    fi
    sleep 3
done
