# Rules for Panalink Development

## 🛡️ Mandate: Zero Regressions (Cero Regresiones)

Every modification to any class must follow a strict "Zero Regression" policy. Before any code is changed, the following functional checklist must be verified, and then re-verified after the change to guarantee no existing features are broken.

---

## 📋 Functional Checklist

### 💬 Mensajería
- [ ] **Envío de mensajes de texto**: Confirm text messages can be composed and sent.
- [ ] **Recepción en tiempo real**: Confirm real-time updates of incoming messages are functional via Supabase Realtime without manually leaving or reopening the screen.
- [ ] **Carga del historial existente**: Verify existing message history loads correctly when entering the chat screen.
- [ ] **Persistencia en Room**: Confirm that messages are persisted to Room database locally for offline-first support.
- [ ] **Sincronización con Supabase**: Verify background sync and database upload sync tasks correctly synchronize states with the Supabase backend.
- [ ] **Mensajes optimistas**: Verify that sending a message immediately places it in the UI with a "sending" state for instant user feedback.
- [ ] **Reemplazo del ID temporal por el definitivo**: Ensure that once Supabase saves the message, the temporary ID (`temp_...`) is replaced in the database and UI by the final remote ID.
- [ ] **Reconexión después de perder Internet**: Confirm that the app gracefully recovers and reconnects to Realtime channels after network loss.

### 📊 Estados del mensaje
- [ ] **Status transitions**: Check that messages move properly through statuses:
  - `sending` -> `sent` -> `delivered` -> `read` -> `failed`
- [ ] **Realtime + Room updates**: Ensure state changes arrive in real-time from the backend and instantly update Room.

### 📡 Señalización (Signaling)
- [ ] **Usuario escribiendo**: Real-time typing indicators are visible and update instantly.
- [ ] **Usuario grabando audio**: Real-time recording indicators are visible and update instantly.
- [ ] **Usuario subiendo archivo**: Real-time uploading indicators function as expected.
- [ ] **Doble tilde gris**: Indicates message was successfully delivered to the remote service.
- [ ] **Doble tilde azul**: Indicates message was read.
- [ ] **Instant update**: All signaling indicators update in real-time without leaving the chat.

### 📦 Multimedia
- [ ] **File type verification**: Check image, video, document, voice note, and stickers.
- [ ] **Performance testing matrix**:
  - Small file uploads and downloads.
  - Large file uploads and downloads (handling large files without memory spikes).
  - Offline mode (queueing files while disconnected).
  - Connection recovery (WorkManager automatically uploads queued media upon regaining internet).
  - App termination (closing the app while uploading, confirming WorkManager resumes successfully).
  - Device reboot (verifying scheduled jobs persist and resume).

### 🎬 Estados (Stories / Status)
- [ ] **Subidas (Uploads)**: Status/stories continue to upload successfully without regressions.
- [ ] **Descargas (Downloads)**: Status media downloads cleanly.
- [ ] **Reproducción (Playback)**: Video/image stories render and play correctly.
- [ ] **WorkManager Isolation**: Confirm that chat's WorkManager migration has absolutely no side-effects on Stories uploading.

### 👤 Perfil (Profile)
- [ ] **Foto de perfil**: Changing the profile picture works flawlessly and uses current local stream/upload logic.

### 📰 Publicaciones (Reels & Feed)
- [ ] **UploadRepository integration**: Verify that feed publications, comments, and other media-heavy screens continue to use `UploadRepository` unmodified and function correctly.

---

## 📊 Phase 1 Post-Implementation Metrics (Rendimiento)
To confirm Phase 1 success, the following metrics must be tracked and presented:
1. Max RAM usage during media upload(must avoid out-of-memory exceptions).
2. Average upload time for media files.
3. Recovery time after connection loss.
4. Resumption time after app force close.
5. Temp files generated vs. successfully cleaned up.
6. Number of full-read `readBytes()` calls eliminated from the main UI thread.

---

## 🛠️ Build & Release (Compilar y Publicar OTA)

### Entorno (sandbox)
* Toolchain persistente: `bash scripts/setup_toolchain.sh` (instala JDK 17 Temurin + Android SDK platform-35/build-tools 35/36 en `/workspace/project/toolchain`).
* Antes de compilar: `source scripts/toolchain_env.sh` (exporta `JAVA_HOME`, `ANDROID_HOME`, `GRADLE_USER_HOME`).
* El `gradle/wrapper/gradle-wrapper.jar` es requerido por el wrapper (si falta, restaurarlo desde `https://raw.githubusercontent.com/gradle/gradle/v9.3.1/gradle/wrapper/gradle-wrapper.jar`).
* `app/google-services.json` NO se commitea: se inyecta desde el secreto `GOOGLE_SERVICES_JSON` (esta gitignoreado).

### Compilar
```bash
source scripts/toolchain_env.sh && ./gradlew :app:assembleDebug            # debug
VERSION_NAME=vX.Y.Z VERSION_CODE=N ./gradlew :app:assembleRelease   # release
```
* Release requiere credenciales de firma: `KEYSTORE_FILE` (puede ser el keystore mismo base64-codificado — el propio `app/build.gradle.kts` lo decodifica con `Base64.getDecoder()` si la ruta no existe; NO materializar en disco), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
* En debug el keystore NO es necesario; en release el build FAILS si faltan credenciales.
* El `google-services.json` real SI se requiere para compilar release: materializarlo desde `GOOGLE_SERVICES_JSON` con `printf '%s' "$GOOGLE_SERVICES_JSON" > app/google-services.json` (gitignoreado, no committear).
* `./gradlew` puede perder el bit de ejecución tras clonar: `chmod +x gradlew` antes de compilar (caso real en sesión 2026-09-04).
* Gotcha secrets y subprocesos: los secrets se inyectan SOLO si su nombre aparece **literal en el comando del terminal**; corren scripts (ej. `bash /tmp/x.sh`) NO los heredan. Pasar los valores como args al script, o invocar el script con el nombre del secret literal en la misma línea.
* Verificar versiones del APK compilado con `aapt dump badging` (p.ej. `<build-tools>/aapt dump badging app-release.apk`) antes de publicar: confirma `versionCode`/`versionName` reales del binario, no solo los del env..

### Canal OTA (Andresaguiar22/panalink-ota)
* Repo público de distribución: `https://github.com/Andresaguiar22/panalink-ota` (rama `main`).
* `manifest.json` en `main` es la fuente de verdad para la app; vivir también se adjunta como asset del release.
* Convención de versiones: `versionCode` incrementa de 1 en 1;; `versionName` es la tag (`v1.3.x`). Actual: **v1.3.34 / code  ​61** (publicada 2026-09-11).
* `minimumSupportedVersionCode` = versionCode de la versión anterior publicada ( 60 para v1.3.34);`mandatory` casi siempre `false`.
* Últimas publicadas(histórico): v1.3.33/code  ​60 (2026-09-11), v1.3.32/code 59 (2026-09-10), v1.3.31/code  ​58 (2026-09-10)y v1.3.30/code  ​⁵⁷ (2026-09-10.
* **Política de build universal (desde v1.3.33):** `app/build.gradle.kts` incluye `packaging { jniLibs { useLegacyPackaging = true } } }` con las 4 ABIs→ el APK sale con `extractNativeLibs=true` (fix de instalación en XOS/Transsion - Infinix/Tecno/itel y ROMs estrictas Android   7-11+) y `Panalink-<versionName>.apk` de ~78 MB. Adjuntar también `manifest.json` al release.
* `sha256` del APK es obligatorio en el manifest (64 hex).

### Publicación OTA (vía GitHub API — usar `GITHUB_PERSONAL_ACCESS_TOKEN_OTA`)
1. Compilar release (ver arriba,)y computar `sha256sum`; verificadar versiones con `aapt dump badging`.
2. Crear el **cuerpo JSON del release com archivo** (`/tmp/release_body.json` con `file_editor`, NUNCA con heredoc ni `jq -n` en terminal — el canal terminal corrompe concatenaciones). POST `/repos/Andresaguiar22/panalink-ota/releases` (draft=true, tag=`vX.Y.Z`, target=`main`, body=changelog+sha256+nota IA) con `--data-binary @file` y `Accept: application/vnd.github+json`.
3. Subir assets al release (draft): APK (`Panalink-<tag>.apk`, `Content-Type: application/vnd.android.package-archive`) y `manifest.json` (`application/json`), con `--data-binary @file` contra `https://uploads.github.com/repos/.../releases/<id>/assets?name=<name>`.
 El APK de ~140 MB sube en ~5 s..
4. PUT `/repos/.../contents/manifest.json` en main con el manifest nuevo(usar `sha` actual del blob — obtenerlo con GET contents/ y construir el payload base64 con archivo+jq/`base64`, no inline frágil).
5. PATCH `/repos/.../releases/<id>` → `draft: false` para publicar; verificadar con GET que `draft=false` y 2 assets..
6. Verificar que `raw.githubusercontent.com/.../main/manifest.json` sirve ya el código/version nuevos (fuente de verdad viva).

### Changelog
* Formato markdown con bullets; ej. `Migración a Panalink V2.0 Oficial con correcciones de CDN y Avatares`.
* Incluir SIEMPRE el `SHA-256:` del APK en el body del release y la nota: `_Release creado por un agente de IA (OpenHands) en nombre del mantenedor._`

---

## 🤖 Instrucciones para agentes Kilo Code / OpenHands

### Objetivo
Esta sección existe para que cualquier agente pueda clonar este repo, preparar el toolchain y compilar la app sin protocolos manuales adicionales.

### Prerrequisitos
- Linux amd64 con bash, curl, unzip/zip, git y Java 11+ disponible para ejecutar `keytool` si se necesita importar certificados.
- Acceso a Internet para descargar JDK 17, Android SDK y dependencias de Gradle.
- El repo clonado en cualquier ruta (el setup es portable).

### Setup automático del toolchain
```bash
bash scripts/setup_toolchain.sh
source scripts/toolchain_env.sh
```
Qué hace:
- Instala JDK 17 Temurin en `/workspace/project/toolchain/jdk17`.
- Instala Android SDK (`platform-35`, `build-tools 35.0.0/36.0.0`, `platform-tools`) en `/workspace/project/toolchain/sdk`.
- Genera `scripts/toolchain_env.sh` con `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `GRADLE_USER_HOME` y `PATH`.
- Genera `secrets.defaults.properties` vacío si falta (requerido por `secrets-gradle-plugin 2.0.1`).
- Genera `app/google-services.json` dummy si falta y no existe `GOOGLE_SERVICES_JSON`.
- Acepta licencias, reintenta `sdkmanager` hasta 4 veces y verifica que el SDK quede completo.

### Compilar debug
```bash
source scripts/toolchain_env.sh
./gradlew --no-daemon :app:compileDebugKotlin
```
Notas:
- Si `./gradlew` pierde el bit de ejecución tras el clone: `chmod +x gradlew`.
- Si el wrapper falla por SSL al bajar Gradle, usar Gradle directo si está cacheado en `/workspace/project/gradle-home/gradle-9.3.1/bin/gradle`, o ejecutar `./gradlew` con `--no-daemon` la primera vez.
- `gradle.properties` ya incluye `javax.net.ssl.trustStore` apuntando al `cacerts` del JDK 17 y `kotlin.daemon.jvmargs=-Xmx4g` para evitar OOM en compilación.

### Compilar release
```bash
printf '%s' "$GOOGLE_SERVICES_JSON" > app/google-services.json
VERSION_NAME=v1.3.Z VERSION_CODE=N ./gradlew --no-daemon :app:assembleRelease
```
- Requiere `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- No commitear `app/google-services.json` ni `secrets.defaults.properties`.

### Troubleshooting rápido
- `Plugin [id: 'org.gradle.toolchains.foojay-resolver-convention' ...]`: ya removido del repo. No debe aparecer.
- `Plugin [id: 'com.android.application', version: '9.1.1'] not found`: revisar conectividad a `dl.google.com` y `repo.maven.apache.org`. El setup ya configura el truststore con el certificado proxy necesario.
- `secrets.defaults.properties could not be found`: correr `bash scripts/setup_toolchain.sh` para generarlo.
- `GC overhead limit exceeded` en Kotlin: verificar `gradle.properties` tenga `kotlin.daemon.jvmargs=-Xmx4g` y `org.gradle.jvmargs=-Xmx4g`.

### Estado actual del build
- `main` compila `:app:compileDebugKotlin` exitosamente en este entorno.
- Si aparecen errores en `VoiceRoomStarmakerComponents.kt`, verificar que no haya caracteres invisibles (`bash scripts/sanitize_invisible.sh`) y que la variable conflictiva en `VoiceRoomSpeakingAura` esté renombrada a `auraScale`/`auraAlpha`.

### Reglas importantes
- No commitear archivos generados por el setup (`secrets.defaults.properties`, `app/google-services.json`, `.toolchain/`, `.gradle-home/`).
- Respetar la regla anti-corrupción de bytes invisibles: editar con `file_editor`, no heredoc; sanitizar tras cada cambio.
---

## 🧠 Conocimiento de arquitectura (mapeado en sesión 2026-09-04)

### Realtime (Supabase)
* **Origen de verdad de mensajes DM**: tabla `public.thread_messages` (NO `messages` — esa es una **vista** (`relkind='v'`) sobre `thread_messages`, y **no se puede publicar** en Realtime). `channel_messages` **NO existe** en `public`.
* **Broadcast vs Postgres Changes**: el trigger `thread_messages_realtime_broadcast` publica en el canal broadcast `chat:{thread_id}` vía `realtime.broadcast_changes`; los eventos quedan en `realtime.messages` (solo topics `chat:*`), que es el buffer de **Broadcast**, no evidencia de Postgres Changes.

* **Postgres Changes NO pasa por `realtime.messages`**; el server lo emite por WAL y nunca se persiste ahí. NO interpretar `realtime.messages` como prueba del estado de Postgres Changes..

* **Suscripciones activas**: `realtime.subscription` es **efímero** (solo clientes conectados en el instante; refleja los joins de probes/WS también; NO acumula histórico). Campos útiles: `id`, `entity::regclass`, `created_at`.
* **Verificación empírica del canal (hecha 2026-09-04, con sonda WS `service_role`)**:
*  - `postgres_changes(public.thread_messages)` → `phx_reply ok` + `system: "Subscribed to PostgreSQL"` — **funciona a nivel de servidor**.,
*  - `postgres_changes(public.channel_messages)` → `phx_reply ok` pero `system: error "Unable to subscribe ... channel_messages"` — **canal fantasma, rechazado**.,
*  - `postgres_changes(public.messages)` (vista) → `system: error "Unable to subscribe ... messages"` — **vistas no son publicables**.,
*  - **Un join fallido NO corta la conexión ni bloquea otros joins**: cada topic es independiente (error por canal, resto intacto. En Android eso genera solo un log silencioso (no rompe el flujo real)..,
* **Trigger clave**: `trg_thread_messages_set_chat_id` (BEFORE INSERT,) hace `new.sender_id := auth.uid()` — **el servidor IMPONE el sender con el usuario autenticado**, even para `service_role`(que deja sender NULL al no haber `auth.uid()`) → **los INSERTs de mensajes SOLO funcionan con el JWT del usuario** (no con service_role).
* **Consumidores del flow realtime( sin duplicación)**:
*  - `MessageRealtimeHandler` (singleton en `MessagesRepository`, scope eterno) **mergea en Room** (vía `MessageFilter` + `messageDao.mergeAndSaveMessage`). Único mutador de Room.,
*  - `PanalinkRealtimeService` escucha el mismo flow pero **solo notifica** (NO muta Room) → sin doble merge ni carreras por diseño.,
* **Parser Android**: responde a topics `realtime:public:*` (`postgres_changes`); **no** a `chat:{thread_id}` (broadcast. El payload del broadcast real viene con columnas camelCase (`text`, `type`, `file_mime`)que el parser no espera (espera snake_case) — riesgo latente si algún día se consume broadcast, no afecta hoy..
* **Estado de la investigación(P3):** join + canal + broadcast confirmados; falta demostración end-to-end con `authenticated`+RLS (prueba A: abrir la app real y ver `realtime.subscription` materializar `thread_messages` con `claims_role=authenticated`). **Cero cambios en Supabase hasta cerrarlo** (trigger/RLS/publications intactos).

### 📹 vCDN (chat)
* **Flujo verificado en código**: los vídeos de chat se enrutan por `vcdn-upload` (edge function con `SUPABASE_SERVICE_ROLE_KEY`) con **fallback automático a B2** si vCDN falla/interrumpe (commit `388408a` — "route chat video through vCDN with B2 fallback").
* **`stable_id`** es la clave de sesión vCDN (claim/heartbeat 60 s; el claim puede recuperarse tras vencimiento; mismo `stable_id` puede reutilizar sesión existente). **No existe TTL/cleanup explícito** para sesiones abandonadas → P2-B auditado (no modificado//cerrado por decisión); el fallback vCDN→B2 **no cancela** la sesión vCDN (stale session riesgo conocido, aceptado por ahora.
* **RLS vCDN NO tocarlo** (correctamente restringido..

---

## 🧼 Regla Anti-Corrupción de Bytes Invisibles (OBLIGATORIA)

Los caracteres Unicode invisibles **U+200B (zero-width space)** y **U+FEFF (BOM)** corrompen código silenciosamente: rompen heredocs, generan `SyntaxError` en Python, ensucian diffs y pueden alterar compilación. Esta sesión los detectó inyectados por el canal de generación de texto del agente. Reglas duras para TODAS las sesiones:

1. **CREAR/EDITAR código con `file_editor`, NUNCA con heredoc** (`cat > file <<'EOF'` está prohibido para código). El heredoc pasa por el canal de terminal que inyecta U+200B..
2. **Después de CADA edición, ejecutar**: `bash scripts/sanitize_invisible.sh` — limpia U+200B/U+FEFF de todos los archivos de texto del repo y reporta si había algo..
  Si reporta archivos, **no committear hasta re-verificar** que el diff siga siendo semánticamente correcto..
3. **Nunca crear scripts Python ni código con `= 0` u otros patrones numéricos inyectados** via terminal;; verificar siempre con `od -c` o `grep -rlP '\x{200B}'` cuando haya duda..
4. **Verificación de integridad antes de cada commit**: `grep -rlP '\x{200B}|\x{FEFF}' . --include='*.kt' --include='*.kts' --include='*.java' --include='*.sh' --include='*.py' --include='*.md' --include='*.json' --include='*.xml' | grep -v '/.git/' | grep -v '/build/'` debe devolver **vacío**. Si devuelve algo, sanitizar y re-inspeccionar..
5. **Herramientas de diagnóstico** (nunca en el repo): usar `/tmp` para archivos de prueba y borrarlos después;; si un archivo `/tmp` queda con U+200B, eliminarlo directamente (como `test_editor1.txt`)..
