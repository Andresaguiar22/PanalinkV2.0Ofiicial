#!/usr/bin/env bash
# =============================================================================
# Build BETA (app de prueba aislada para iterar en ramas de features)
# =============================================================================
# Modalidad de trabajo Panalink (ver AGENTS.md -> "Feature Branch + Beta + Release"):
#   - El equipo trabaja en una rama de feature (ej. kilo/fancy-bloom-c6g).
#   - Este script compila esa rama como app BETA aparte:
#        package com.panalink.app.beta,
 #     label "PanaLink Beta" - NO toca la app real de los usuarios ni sus datos.

# PERSISTE EL WORKTREE: entre builds solo se recompila el delta (rapido).
#
# Uso:
#   bash scripts/build_beta.sh
#
# Env (opcional):
#   BETA_BRANCH        Rama remota a compilar. Def: origin/kilo/fancy-bloom-c6g
#   BETA_WORKTREE     Ruta del worktree. Def: /tmp/panalink_beta
#   BETA_VERSION_NAME  VersionName de la beta. Def: v1.3.39-beta
#   BETA_VERSION_CODE   VersionCode de la beta. Def: a66
#   BETA_OUT            APK de salida. Def: /tmp/Panalink-BETA-apk-debug.apk
# =============================================================================

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BRANCH="${BETA_BRANCH:-origin/kilo/fancy-bloom-c6g}"
WT="${BETA_WORKTREE:-/tmp/panalink_beta}"
VNAME="${BETA_VERSION_NAME:-v1.3.39-beta}"
VCODE="${BETA_VERSION_CODE:-66}"
OUT="${BETA_OUT:-/tmp/Panalink-BETA-apk-debug.apk}"
TOOLCHAIN="$REPO/.toolchain"
AAPT="$TOOLCHAIN/sdk/build-tools/35.0.0/aapt"

export JAVA_HOME="$TOOLCHAIN/jdk17"
export ANDROID_HOME="$TOOLCHAIN/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$REPO/.toolchain/.gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"

cd "$REPO" || exit 11

echo "==> [1/7] Refrescando rama $BRANCH ..."
git fetch origin "${BRANCH#origin/}" >/dev/null 2>&1 || { echo "fallo fetch"; exit  12; }

echo "==> [2/7] Sincronizando worktree $WT ..."
if [ -d "$WT" ]; then
    git worktree remove "$WT" --force >/dev/null 2>&1
    rm -rf "$WT"
fi
git worktree prune >/dev/null 2>&1
git worktree add "$WT" "$BRANCH" >/dev/null 2>&1 || { echo "fallo worktree add"; exit  13; }

echo "==> [3/7] Generando google-services.json BETA (package com.panalink.app.beta) ..."
if [ -f "$REPO/app/google-services.json" ]; then
    jq '.client += [.client[0]] | .client[1].client_info.android_client_info.package_name = "com.panalink.app.beta"' \
        "$REPO/app/google-services.json" > "$WT/app/google-services.json" || { echo "fallo jq google-services"; exit  14; }
else
    echo "ADVERTENCIA: app/google-services.json no existe en main; el build de la beta puede fallar en processGoogleServices."
fi

echo "==> [4/7] Aplicando parches BETA (secrets, label, manifest overlay, suffix) ..."
if [ ! -f "$WT/secrets.defaults.properties" ]; then
    : > "$WT/secrets.defaults.properties"
fi
# La app SIEMPRE lleva la conexion de la API de GIFs: se inyecta la key
# (KLIPY / Giphy) desde el entorno del build al secrets del worktree, asi
# cada APK compilado trae la API funcional sin depender de pasos manuales.

_write_gif_keys() {
  for _key in KLIPY_API_KEY GIPHY_API_KEY; do
    _val="${!_key:-}"
    [ -z "$_val" ] && continue
    if [ -f "$1" ]; then
      grep -v "^${_key}=" "$1" > "$1.tmp" || true
      mv "$1.tmp" "$1"
    fi
    printf '%s=%s\n' "$_key" "$_val" >> "$1"
  done
}

_write_gif_keys "$WT/secrets.properties"

mkdir -p "$WT/app/src/debug/res/values"

cat > "$WT/app/src/debug/res/values/strings.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">PanaLink Beta</string>
</resources>
EOF

cat > "$WT/app/src/debug/AndroidManifest.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application
        android:label="@string/app_name"
        tools:replace="android:label" />
</manifest>
EOF

# Keystore fijo de la beta: firma estable para TODAS las rondas (se instala encima, sin conflicto).
cp "$REPO/app/panalink-beta.keystore" "$WT/app/panalink-beta.keystore" || { echo "fallo copia keystore"; exit  15; }

python3 - "$WT/app/build.gradle.kts" <<'PY'
import sys, pathlib, re
p = pathlib.Path(sys.argv[1])
s = p.read_text()

debug_signing = '''

    signingConfigs {
        create("beta") {
            storeFile = file("panalink-beta.keystore")
            storePassword = "panalinkbeta"
            keyAlias = "panalinkbeta"
            keyPassword = "panalinkbeta"
            isV1SigningEnabled = true
            isV2SigningEnabled = true
        }
    }
'''
if "storeFile = file(\"panalink-beta.keystore\")" not in s:
    # inserta el signingConfig "beta" justo antes del buildTypes.

    m = re.search(r"\n(\s*)buildTypes\s*\{", s)
    if not m:
        print("ERROR: no se encontro 'buildTypes {' en build.gradle.kts")
        sys.exit(1)
    indent = m.group(1)
    block = debug_signing.replace("\n    ", "\n" + indent).rstrip() + "\n"
    s = s[:m.start(1)] + block + s[m.start(1):]

target = re.compile(r"debug\s*\{\s*\}")
if "applicationIdSuffix" in s:
    print("   gradle ya tiene suffix .beta; sin cambios")
else:
    replacement = ("debug {\n"
    "            // Variante BETA de prueba: instala como app aparte (com.panalink.app.beta),\n"
    "            // sin chocar con la produccion. Temporal, no commitear.\n"
    "            applicationIdSuffix = \".beta\"\n"
    "            signingConfig = signingConfigs.getByName(\"beta\")\n"
    "        }")
    s, n = target.subn(replacement, s, count=1)
    if n == 0:
        print("ERROR: no se encontro el bloque 'debug { }' en build.gradle.kts")
        sys.exit(1)

# Solo ABIs de telefonos reales: las de emulador (x86/x86_64) son ~29 MB y el
# APK de 93 MB se cortaba al descargar en el movil ("paquete invalido").
if "abiFilters" not in s:
    m2 = re.search(r"defaultConfig\s*\{", s)
    if not m2:
        print("ERROR: no se encontro 'defaultConfig {' en build.gradle.kts")
        sys.exit(1)
    s = (s[:m2.end()]
         + '\n        ndk {\n            abiFilters += listOf("arm64-v8a", "armeabi-v7a")\n        }'
         + s[m2.end():])

p.write_text(s)
print("   gradle parchado: firma beta estable + suffix .beta + ABIs ARM")
PY
if [ $? -ne 0 ]; then exit  16; fi

echo "==> [5/7] Compilando APK beta ..."
cd "$WT" || exit 16
chmod +x gradlew
VERSION_NAME="$VNAME" VERSION_CODE="$VCODE" ./gradlew --no-daemon :app:assembleDebug >/tmp/build_beta.log 2>&1
if [ $? -ne 0 ]; then
    echo "BUILD FALLO - ver /tmp/build_beta.log"
    tail -20 /tmp/build_beta.log
    exit  17
fi

cp "$WT/app/build/outputs/apk/debug/app-debug.apk" "$OUT" || exit 18

echo "==> [6/7] Verificando paquete y version..."
"$AAPT" dump badging "$OUT" 2>/dev/null | grep -E "^package:|^application-label:"

echo "==> [7/7] SHA256:"
sha256sum "$OUT" | awk '{print $1}'

echo ""
echo "✅ APK BETA listo: $OUT"
echo "   URL fija de descarga (servidor HTTP manual):"
echo "   https://work-1-kpffhphchmmnrsin.prod-runtime.all-hands.dev/Panalink-BETA-apk-debug.apk"