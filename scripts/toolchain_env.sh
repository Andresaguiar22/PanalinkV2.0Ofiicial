# Generado por setup_toolchain.sh — source para compilar Panalink.
# Portable: ruta relativa al repo (el sandbox cambia su ruta entre sesiones).
_TC_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.toolchain"
export JAVA_HOME="$_TC_ROOT/jdk17"
export ANDROID_HOME="$_TC_ROOT/sdk"
export ANDROID_SDK_ROOT="$_TC_ROOT/sdk"
export GRADLE_USER_HOME="$_TC_ROOT/.gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"
