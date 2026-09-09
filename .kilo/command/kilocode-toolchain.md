# Kilo Code Toolchain

Setup idempotente del entorno de compilación para el proyecto **Panalink** en sandboxes/agentes Kilo Code.

## Qué hace

- Instala **JDK 17 Temurin** en `.toolchain/jdk17`
- Instala **Android SDK** (platform-35, build-tools 35.0.0/36.0.0, platform-tools) en `.toolchain/sdk`
- Acepta licencias de Android SDK con reintentos y fallback HTTPS si es necesario
- Configura el **truststore del JDK** con el certificado del proxy SSL saliente para resolver handshakes en entornos con proxy transparente
- Genera `scripts/toolchain_env.sh` con `PROJECT_DIR`, `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `GRADLE_USER_HOME`
- Genera/actualiza `settings.gradle.kts` con orden de repositorios y mirrors para resolver plugins/dependencias (Huawei, Aliyun, Maven Central, JitPack)
- Genera `app/google-services.json` dummy si no existe (solo para compilar debug; no usar en release/OTA)
- Genera `secrets.defaults.properties` vacío si falta (requerido por `secrets-gradle-plugin`)
- Asegura que `gradlew` tenga permiso de ejecución

## Uso

```bash
bash .kilo/command/kilocode-toolchain.sh
export PROJECT_DIR="$PWD"
source scripts/toolchain_env.sh
./gradlew --no-daemon :app:compileDebugKotlin
```

## Notas

- Es idempotente: volver a ejecutarlo solo instala lo que falte.
- `GRADLE_USER_HOME` apunta a `.gradle-home/` para persistir el caché entre sesiones.
- `gradle/wrapper/gradle-wrapper.properties` debe usar Gradle 9.3.1 local o remoto según el entorno.
- `PROJECT_DIR` se exporta en `toolchain_env.sh` y puede usarse en `gradle.properties` para configurar el truststore del JDK.
- Si Gradle falla por resolución de plugins/dependencias, verificar que `settings.gradle.kts` tenga los mirrors Huawei/Aliyun antes de `mavenCentral()` y `jitpack.io` primero en `dependencyResolutionManagement`.
- Para forzar la reescritura de `settings.gradle.kts`: `KILO_TOOLCHAIN_UPDATE_SETTINGS=true bash .kilo/command/kilocode-toolchain.sh`
