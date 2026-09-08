#!/usr/bin/env bash
# Panalink — Toolchain de build persistente para cualquier sandbox/agente.
#
# Instala JDK 17 (Temurin) + Android SDK (platform 35, build-tools 35/36) en
# un directorio .toolchain/ dentro del repo, que sobrevive a los resets del
# sandbox. Tambien fija GRADLE_USER_HOME para no re-descargar Gradle
# en cada sesion, genera un app/google-services.json dummy si falta, y
# genera secrets.defaults.properties vacio si falta (requerido por el secrets plugin).
#
# Es idempotente: ejecutarlo de nuevo solo instala lo que falte.

# Uso:
#   bash scripts/setup_toolchain.sh        # instala solo lo que falte (idempotente)
#   source scripts/toolchain_env.sh        # exporta JAVA_HOME/ANDROID_HOME/GRADLE_USER_HOME
#
# Compilar despues:
#   source scripts/toolchain_env.sh && ./gradlew :app:compileDebugKotlin

set -u

# Detecta la raiz real del repo (portable: no asume /workspace/project).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TOOLCHAIN_DIR="${TOOLCHAIN_DIR:-$PROJECT_DIR/.toolchain}"
JDK_DIR="$TOOLCHAIN_DIR/jdk17"
SDK_DIR="$TOOLCHAIN_DIR/sdk"
GRADLE_HOME_DIR="$PROJECT_DIR/.gradle-home"

CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
JDK_API_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"

log() { printf '[setup-toolchain] %s\n' "$*"; }

mkdir -p "$TOOLCHAIN_DIR" "$GRADLE_HOME_DIR"

# ---------------------------------------------------------------- JDK 17 ----
if [ -x "$JDK_DIR/bin/java" ]; then
  log "JDK 17 ya instalado en $JDK_DIR"
else
  log "Descargando JDK 17 (Temurin)..."
  tmp_jdk="$TOOLCHAIN_DIR/jdk17.tar.gz"
  curl -fL --retry 3 -o "$tmp_jdk" "$JDK_API_URL" || {
    log "ERROR: fallo la descarga del JDK"; exit 1;
  }
  rm -rf "$JDK_DIR" "$TOOLCHAIN_DIR/jdk17-extract"
  mkdir -p "$TOOLCHAIN_DIR/jdk17-extract"
  tar -xzf "$tmp_jdk" -C "$TOOLCHAIN_DIR/jdk17-extract" --strip-components=1
  mv "$TOOLCHAIN_DIR/jdk17-extract" "$JDK_DIR"
  rm -f "$tmp_jdk"
  log "JDK instalado: $("$JDK_DIR/bin/java" -version 2>&1 | head -1)"
fi

# ------------------------------------------------------------- Android SDK ----
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"

SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  log "Descargando Android command-line tools..."
  tmp_sdk="$TOOLCHAIN_DIR/$CMDLINE_TOOLS_ZIP"
  curl -fL --retry 3 -o "$tmp_sdk" "$CMDLINE_TOOLS_URL" || {
    log "ERROR: fallo la descarga de command-line tools"; exit 1;
  }
  rm -rf "$SDK_DIR/cmdline-tools"
  mkdir -p "$SDK_DIR/cmdline-tools"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$tmp_sdk" -d "$SDK_DIR/cmdline-tools"
  else
    python3 -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" \
      "$tmp_sdk" "$SDK_DIR/cmdline-tools"
  fi
  mkdir -p "$SDK_DIR/cmdline-tools/latest"
  mv "$SDK_DIR/cmdline-tools/cmdline-tools"/* "$SDK_DIR/cmdline-tools/latest/"
  rm -rf "$SDK_DIR/cmdline-tools/cmdline-tools" "$tmp_sdk"
  # python-zipfile no preserva permisos de ejecucion
  chmod -R a+rx "$SDK_DIR/cmdline-tools/latest/bin" "$SDK_DIR/cmdline-tools/latest/lib" 2>/dev/null || true
fi

log "Aceptando licencias e instalando platform-35 + build-tools..."
yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses >/dev/null 2>&1 || true
# sdkmanager es propenso a fallos transitorios de red: reintenta hasta 4 veces.

for attempt in 1 2 3 4; do
  if "$SDKMANAGER" --sdk_root="$SDK_DIR" \
    "platform-tools" "platforms;android-35" "build-tools;35.0.0" "build-tools;36.0.0" \
    >/dev/null 2>&1; then
    break
  fi
  log "sdkmanager fallo en intento $attempt/4 - reintentando..."
  sleep 5
done

# Verifica que el SDK quedo completo antes de continuar.

if [ ! -d "$SDK_DIR/platforms/android-35" ] || [ ! -d "$SDK_DIR/build-tools/35.0.0" ] || [ ! -d "$SDK_DIR/build-tools/36.0.0" ] || [ ! -x "$SDK_DIR/platform-tools/adb" ]; then
  log "ERROR: el Android SDK quedo incompleto tras los reintentos."
  log "  Revisa la conectividad hacia dl.google.com y volve a ejecutar:"
  log "  bash scripts/setup_toolchain.sh"
  exit 1
fi

# ---------------------------------------------------- google-services.json ----
# Si GOOGLE_SERVICES_JSON esta definida (secreto OpenHands), SIEMPRE escribir el
# real. Un dummy empaquetado en release deja Firebase/FCM muertos en produccion
# (causa raiz de "push nunca suena" en v1.0.5).
if [ -n "${GOOGLE_SERVICES_JSON:-}" ]; then
  log "Escribiendo app/google-services.json REAL desde \$GOOGLE_SERVICES_JSON"
  printf '%s' "$GOOGLE_SERVICES_JSON" > "$PROJECT_DIR/app/google-services.json"
elif [ ! -f "$PROJECT_DIR/app/google-services.json" ]; then
  log "Generando app/google-services.json dummy (solo para compilar; no commitear)"
  log "ADVERTENCIA: NO hacer builds release/OTA con este dummy - FCM queda muerto"
  cat > "$PROJECT_DIR/app/google-services.json" <<'EOF'
{
  "project_info": { "project_number": "000000000000", "project_id": "panalink-dummy", "storage_bucket": "panalink-dummy.appspot.com" },
  "client": [
    {
      "client_info": { "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000", "android_client_info": { "package_name": "com.panalink.app" } },
      "oauth_client": [],
      "api_key": [ { "current_key": "dummy" } ],
      "services": { "appinvite_service": { "other_platform_oauth_client": [] } }
    }
  ],
  "configuration_version": "1"
}
EOF
fi

# ---------------------------------------------- secrets.defaults.properties ----
# El secrets-gradle-plugin 2.0.1 lanza en configuracion si el archivo de
# defaults no existe: crearlo vacio si falta (secrets.properties real opcional).
if [ ! -f "$PROJECT_DIR/secrets.defaults.properties" ]; then
  log "Generando secrets.defaults.properties vacio (requerido por el secrets plugin)"
  : > "$PROJECT_DIR/secrets.defaults.properties"
fi

# --------------------------------------------------------------- env file ----
cat > "$PROJECT_DIR/scripts/toolchain_env.sh" <<EOF
# Generado por setup_toolchain.sh — source para compilar Panalink.
export JAVA_HOME="$JDK_DIR"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export GRADLE_USER_HOME="$GRADLE_HOME_DIR"
export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/platform-tools:\$PATH"
EOF

# ---------------------------------------------------------- gradlew permiso ----
if [ -f "$PROJECT_DIR/gradlew" ] && [ ! -x "$PROJECT_DIR/gradlew" ]; then
  chmod +x "$PROJECT_DIR/gradlew"
  log "gradlew: permiso de ejecucion agregado"
fi

log "Listo. Para compilar:"
log "  source scripts/toolchain_env.sh && ./gradlew :app:compileDebugKotlin"
