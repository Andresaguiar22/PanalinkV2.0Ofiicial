#!/usr/bin/env bash
# Kilo Code Toolchain — setup idempotente para compilar Panalink en sandbox/agente.
#
# Instala JDK 17 (Temurin) + Android SDK (platform 35, build-tools 35/36) en
# .toolchain/, configura el truststore para proxy SSL, acepta licencias,
# genera google-services.json dummy si falta, secrets.defaults.properties,
# y exporta JAVA_HOME/ANDROID_HOME/GRADLE_USER_HOME en scripts/toolchain_env.sh.
#
# Uso:
#   bash .kilo/command/kilocode-toolchain.sh        # setup completo
#   source scripts/toolchain_env.sh                 # exporta entorno
#   ./gradlew --no-daemon :app:compileDebugKotlin   # compilar

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
TOOLCHAIN_DIR="${TOOLCHAIN_DIR:-$PROJECT_DIR/.toolchain}"
JDK_DIR="$TOOLCHAIN_DIR/jdk17"
SDK_DIR="$TOOLCHAIN_DIR/sdk"
GRADLE_HOME_DIR="$PROJECT_DIR/.gradle-home"

CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
JDK_API_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"

log() { printf '[kilocode-toolchain] %s\n' "$*"; }

mkdir -p "$TOOLCHAIN_DIR" "$GRADLE_HOME_DIR"

# ---------------------------------------------------------------- JDK 17 ----
if [ -x "$JDK_DIR/bin/java" ]; then
  log "JDK 17 ya instalado en $JDK_DIR"
else
  log "Descargando JDK 17 (Temurin)..."
  tmp_jdk="$TOOLCHAIN_DIR/jdk17.tar.gz"
  curl -fL --retry 3 -o "$tmp_jdk" "$JDK_API_URL" || {
    log "ERROR: fallo la descarga del JDK"; exit 1
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
    log "ERROR: fallo la descarga de command-line tools"; exit 1
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
  mv "$SDK_DIR/cmdline-tools/cmdline-tools"/* "$SDK_DIR/cmdline-tools/latest/" 2>/dev/null || true
  rm -rf "$SDK_DIR/cmdline-tools/cmdline-tools" "$tmp_sdk"
  chmod -R a+rx "$SDK_DIR/cmdline-tools/latest/bin" "$SDK_DIR/cmdline-tools/latest/lib" 2>/dev/null || true
fi

# Licencias
log "Aceptando licencias..."
licenses_log="$TOOLCHAIN_DIR/sdk_licenses.log"
yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses > "$licenses_log" 2>&1 || true
if grep -q "All SDK package licenses accepted" "$licenses_log"; then
  log "Licencias aceptadas correctamente."
else
  log "ADVERTENCIA: puede haber problemas con las licencias del SDK. Revisar $licenses_log"
fi

# Proxy SSL truststore
log "Configurando truststore para proxy SSL..."
proxy_all="$TOOLCHAIN_DIR/proxy-all.pem"
proxy_ca="$TOOLCHAIN_DIR/proxy-ca.pem"
if echo | openssl s_client -connect dl.google.com:443 -servername dl.google.com -showcerts 2>/dev/null \
  | sed -n '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/p' > "$proxy_all"; then
  count=$(grep -c -- '-----BEGIN CERTIFICATE-----' "$proxy_all" 2>/dev/null || echo 0)
  if [ "$count" -gt 0 ]; then
    awk "/-----BEGIN CERTIFICATE-----/{n++} n==$count" "$proxy_all" > "$proxy_ca"
    if [ -s "$proxy_ca" ]; then
      keytool -delete -alias proxy-ca -keystore "$JDK_DIR/lib/security/cacerts" -storepass changeit >/dev/null 2>&1 || true
      keytool -import -trustcacerts -keystore "$JDK_DIR/lib/security/cacerts" -storepass changeit -alias proxy-ca -file "$proxy_ca" -noprompt >/dev/null 2>&1 || true
      log "Certificado del proxy importado al truststore del JDK."
    fi
  fi
else
  log "ADVERTENCIA: no se pudo extraer el certificado del proxy. El build puede fallar por SSL."
fi

# Paquetes SDK
log "Instalando platform-35 + build-tools..."
for attempt in 1 2 3 4; do
  install_log="$TOOLCHAIN_DIR/sdk_install_${attempt}.log"
  if "$SDKMANAGER" --sdk_root="$SDK_DIR" --no_https \
     "platform-tools" "platforms;android-35" "build-tools;35.0.0" "build-tools;36.0.0" \
     > "$install_log" 2>&1; then
    break
  fi
  log "sdkmanager fallo en intento $attempt/4 - reintentando..."
  sleep 5
done
if [ ! -d "$SDK_DIR/platforms/android-35" ] || [ ! -d "$SDK_DIR/build-tools/35.0.0" ] || [ ! -d "$SDK_DIR/build-tools/36.0.0" ] || [ ! -x "$SDK_DIR/platform-tools/adb" ]; then
  log "ERROR: el Android SDK quedo incompleto tras los reintentos."
  log "  Revisa la conectividad hacia dl.google.com y volve a ejecutar:"
  log "  bash .kilo/command/kilocode-toolchain.sh"
  exit 1
fi

# google-services.json
if [ -n "${GOOGLE_SERVICES_JSON:-}" ]; then
  log "Escribiendo app/google-services.json REAL desde \$GOOGLE_SERVICES_JSON"
  printf '%s' "$GOOGLE_SERVICES_JSON" > "$PROJECT_DIR/app/google-services.json"
elif [ ! -f "$PROJECT_DIR/app/google-services.json" ]; then
  log "Generando app/google-services.json dummy (solo para compilar; no commitear)"
  log "ADVERTENCIA: NO hacer builds release/OTA con este dummy - FCM queda muerto"
  printf '%s\n' \
    '{' \
    '  "project_info": { "project_number": "000000000000", "project_id": "panalink-dummy", "storage_bucket": "panalink-dummy.appspot.com" },' \
    '  "client": [' \
    '    {' \
    '      "client_info": { "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000", "android_client_info": { "package_name": "com.panalink.app" } },' \
    '      "oauth_client": [],' \
    '      "api_key": [ { "current_key": "dummy" } ],' \
    '      "services": { "appinvite_service": { "other_platform_oauth_client": [] } }' \
    '    }' \
    '  ],' \
    '  "configuration_version": "1"' \
    '}' \
    > "$PROJECT_DIR/app/google-services.json"
fi

# secrets.defaults.properties
if [ ! -f "$PROJECT_DIR/secrets.defaults.properties" ]; then
  log "Generando secrets.defaults.properties vacio (requerido por el secrets plugin)"
  : > "$PROJECT_DIR/secrets.defaults.properties"
fi

# toolchain_env.sh
{
  printf '# Generado por kilocode-toolchain.sh — source para compilar Panalink.\n'
  printf 'export JAVA_HOME="%s"\n' "$JDK_DIR"
  printf 'export ANDROID_HOME="%s"\n' "$SDK_DIR"
  printf 'export ANDROID_SDK_ROOT="%s"\n' "$SDK_DIR"
  printf 'export GRADLE_USER_HOME="%s"\n' "$GRADLE_HOME_DIR"
  printf 'export PATH="%s/bin:%s/platform-tools:%s"\n' "$JDK_DIR" "$SDK_DIR" "$PATH"
} > "$PROJECT_DIR/scripts/toolchain_env.sh"

# gradlew permiso
if [ -f "$PROJECT_DIR/gradlew" ] && [ ! -x "$PROJECT_DIR/gradlew" ]; then
  chmod +x "$PROJECT_DIR/gradlew"
  log "gradlew: permiso de ejecucion agregado"
fi

log "Listo. Para compilar:"
log "  source scripts/toolchain_env.sh && ./gradlew :app:compileDebugKotlin"
