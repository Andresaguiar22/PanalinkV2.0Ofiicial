# Generado por setup_toolchain.sh — source para compilar Panalink.
# Rutas relativas al repo (portable): sobrevive a resets/recreaciones del sandbox.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TOOLCHAIN_DIR="${TOOLCHAIN_DIR:-$PROJECT_DIR/.toolchain}"
export JAVA_HOME="$TOOLCHAIN_DIR/jdk17"
export ANDROID_HOME="$TOOLCHAIN_DIR/sdk"
export ANDROID_SDK_ROOT="$TOOLCHAIN_DIR/sdk"
export GRADLE_USER_HOME="$PROJECT_DIR/.gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
