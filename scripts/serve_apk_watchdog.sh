#!/usr/bin/env bash
# Supervisor del servidor de APK: relanza serve_apk.py si el proceso muere.
#
# Por que existe: el servidor se cae cuando se recicla la sesion del sandbox y el
# ingress empieza a devolver 502. Este loop lo vuelve a levantar en segundos sin
# intervencion. OJO: si el sandbox se REINICIA del todo, este script tambien muere
# (hay que relanzarlo); lo que cubre son caidas sueltas del proceso.
#
# Uso: bash scripts/serve_apk_watchdog.sh [puerto] [dir]
set -u

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${1:-12001}"
DIR="${2:-$REPO/.toolchain/serve_apk}"
LOG="/tmp/serve_apk_${PORT}.log"

mkdir -p "$DIR"

echo "watchdog: puerto=$PORT dir=$DIR log=$LOG" >> "$LOG"

while true; do
    if ! ss -tln 2>/dev/null | grep -q ":${PORT} "; then
        echo "$(date '+%F %T') relanzando serve_apk en $PORT" >> "$LOG"
        python3 "$REPO/scripts/serve_apk.py" "$PORT" "$DIR" >> "$LOG" 2>&1
        echo "$(date '+%F %T') serve_apk termino; reintento en 3s" >> "$LOG"
    fi
    sleep 3
done
