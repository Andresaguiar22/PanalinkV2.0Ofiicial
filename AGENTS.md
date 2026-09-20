# Rules for Panalink Development

## 🚫 Regla 1: Prohibido el uso de Code Explorer / code-explorer

**Terminantemente prohibido** usar el agente `code-explorer` (codeexplorer) para explorar o entender este codebase. En su lugar, explorar el código directamente con herramientas propias (terminal con `grep`/`find`/`sed`/`awk`, y `file_editor` en modo `view`).

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
* Release requiere credenciales de firma: `KEYSTORE_FILE` (puede ser el keystore mismo base64-codificado - el propio `app/build.gradle.kts` lo decodifica con `Base64.getDecoder()` si la ruta no existe; NO materializar en disco), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
* En debug el keystore NO es necesario; en release el build FAILS si faltan credenciales.
* El `google-services.json` real SI se requiere para compilar release: materializarlo desde `GOOGLE_SERVICES_JSON` con `printf '%s' "$GOOGLE_SERVICES_JSON" > app/google-services.json` (gitignoreado, no committear).
* `./gradlew` puede perder el bit de ejecución tras clonar: `chmod +x gradlew` antes de compilar (caso real en sesión 2026-09-04).
* Gotcha secrets y subprocesos: los secrets se inyectan SOLO si su nombre aparece **literal en el comando del terminal**; corren scripts (ej. `bash /tmp/x.sh`) NO los heredan. Pasar los valores como args al script, o invocar el script con el nombre del secret literal en la misma línea.
* Verificar versiones del APK compilado con `aapt dump badging` (p.ej. `<build-tools>/aapt dump badging app-release.apk`) antes de publicar: confirma `versionCode`/`versionName` reales del binario, no solo los del env..

### Canal OTA (Andresaguiar22/panalink-ota)
* Repo público de distribución: `https://github.com/Andresaguiar22/panalink-ota` (rama `main`).
* `manifest.json` en `main` es la fuente de verdad para la app; vivir también se adjunta como asset del release.
* Convención de versiones: `versionCode` incrementa de 1 en 1;; `versionName` es la tag (`v1.3.x`). Actual: **v1.3.35 / code  62** (publicada 2026-09-11).
* `minimumSupportedVersionCode` = versionCode de la versión anterior publicada ( 61 para v1.3.35);`mandatory` casi siempre `false`.
* Últimas publicadas(histórico): v1.3.34/code 61 (2026-09-11), v1.3.33/code  60 (2026-09-11), v1.3.32/code 59 (2026-09-10), v1.3.31/code  58 (2026-09-10)y v1.3.30/code  ⁵⁷ (2026-09-10.
* **Política de build universal (desde v1.3.33):** `app/build.gradle.kts` incluye `packaging { jniLibs { useLegacyPackaging = true } } }` con las 4 ABIs→ el APK sale con `extractNativeLibs=true` (fix de instalación en XOS/Transsion - Infinix/Tecno/itel y ROMs estrictas Android   7-11+) y `Panalink-<versionName>.apk` de ~78 MB. Adjuntar también `manifest.json` al release.
* `sha256` del APK es obligatorio en el manifest (64 hex).

### Publicación OTA (vía GitHub API - usar `GITHUB_PERSONAL_ACCESS_TOKEN_OTA`)
1. Compilar release (ver arriba,)y computar `sha256sum`; verificadar versiones con `aapt dump badging`.
2. Crear el **cuerpo JSON del release com archivo** (`/tmp/release_body.json` con `file_editor`, NUNCA con heredoc ni `jq -n` en terminal - el canal terminal corrompe concatenaciones). POST `/repos/Andresaguiar22/panalink-ota/releases` (draft=true, tag=`vX.Y.Z`, target=`main`, body=changelog+sha256+nota IA) con `--data-binary @file` y `Accept: application/vnd.github+json`.
3. Subir assets al release (draft): APK (`Panalink-<tag>.apk`, `Content-Type: application/vnd.android.package-archive`) y `manifest.json` (`application/json`), con `--data-binary @file` contra `https://uploads.github.com/repos/.../releases/<id>/assets?name=<name>`.
 El APK de ~140 MB sube en ~5 s..
4. PUT `/repos/.../contents/manifest.json` en main con el manifest nuevo(usar `sha` actual del blob - obtenerlo con GET contents/ y construir el payload base64 con archivo+jq/`base64`, no inline frágil).
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
* **Origen de verdad de mensajes DM**: tabla `public.thread_messages` (NO `messages` - esa es una **vista** (`relkind='v'`) sobre `thread_messages`, y **no se puede publicar** en Realtime). `channel_messages` **NO existe** en `public`.
* **Broadcast vs Postgres Changes**: el trigger `thread_messages_realtime_broadcast` publica en el canal broadcast `chat:{thread_id}` vía `realtime.broadcast_changes`; los eventos quedan en `realtime.messages` (solo topics `chat:*`), que es el buffer de **Broadcast**, no evidencia de Postgres Changes.

* **Postgres Changes NO pasa por `realtime.messages`**; el server lo emite por WAL y nunca se persiste ahí. NO interpretar `realtime.messages` como prueba del estado de Postgres Changes..

* **Suscripciones activas**: `realtime.subscription` es **efímero** (solo clientes conectados en el instante; refleja los joins de probes/WS también; NO acumula histórico). Campos útiles: `id`, `entity::regclass`, `created_at`.
* **Verificación empírica del canal (hecha 2026-09-04, con sonda WS `service_role`)**:
*  - `postgres_changes(public.thread_messages)` → `phx_reply ok` + `system: "Subscribed to PostgreSQL"` - **funciona a nivel de servidor**.,
*  - `postgres_changes(public.channel_messages)` → `phx_reply ok` pero `system: error "Unable to subscribe ... channel_messages"` - **canal fantasma, rechazado**.,
*  - `postgres_changes(public.messages)` (vista) → `system: error "Unable to subscribe ... messages"` - **vistas no son publicables**.,
*  - **Un join fallido NO corta la conexión ni bloquea otros joins**: cada topic es independiente (error por canal, resto intacto. En Android eso genera solo un log silencioso (no rompe el flujo real)..,
* **Trigger clave**: `trg_thread_messages_set_chat_id` (BEFORE INSERT,) hace `new.sender_id := auth.uid()` - **el servidor IMPONE el sender con el usuario autenticado**, even para `service_role`(que deja sender NULL al no haber `auth.uid()`) → **los INSERTs de mensajes SOLO funcionan con el JWT del usuario** (no con service_role).
* **Consumidores del flow realtime( sin duplicación)**:
*  - `MessageRealtimeHandler` (singleton en `MessagesRepository`, scope eterno) **mergea en Room** (vía `MessageFilter` + `messageDao.mergeAndSaveMessage`). Único mutador de Room.,
*  - `PanalinkRealtimeService` escucha el mismo flow pero **solo notifica** (NO muta Room) → sin doble merge ni carreras por diseño.,
* **Parser Android**: responde a topics `realtime:public:*` (`postgres_changes`); **no** a `chat:{thread_id}` (broadcast. El payload del broadcast real viene con columnas camelCase (`text`, `type`, `file_mime`)que el parser no espera (espera snake_case) - riesgo latente si algún día se consume broadcast, no afecta hoy..
* **Estado de la investigación(P3):** join + canal + broadcast confirmados; falta demostración end-to-end con `authenticated`+RLS (prueba A: abrir la app real y ver `realtime.subscription` materializar `thread_messages` con `claims_role=authenticated`). **Cero cambios en Supabase hasta cerrarlo** (trigger/RLS/publications intactos).

### 📹 vCDN (chat)
* **Flujo verificado en código**: los vídeos de chat se enrutan por `vcdn-upload` (edge function con `SUPABASE_SERVICE_ROLE_KEY`) con **fallback automático a B2** si vCDN falla/interrumpe (commit `388408a` - "route chat video through vCDN with B2 fallback").
* **`stable_id`** es la clave de sesión vCDN (claim/heartbeat 60 s; el claim puede recuperarse tras vencimiento; mismo `stable_id` puede reutilizar sesión existente). **No existe TTL/cleanup explícito** para sesiones abandonadas → P2-B auditado (no modificado//cerrado por decisión); el fallback vCDN→B2 **no cancela** la sesión vCDN (stale session riesgo conocido, aceptado por ahora.
* **RLS vCDN NO tocarlo** (correctamente restringido..

---
### 📹 Reels (TikTokVideoFeedScreen) - pipeline y fixes (sesión 2026-09-11)
* **Pipeline de reproducción**: `TikTokVideoFeedScreen` usa `ReelDualPlayerManager` (slot A + slot B, un player por slot, `exoPlayerRef` apunta al activo reciclado) + `CdnManager.resolveMediaUrlFresh` (URL firmada VCDN). El preload solo del **siguiente** reel (`isPreload = page <= pagerState.currentPage + 1`): el slot B queda prepped con URL ya resuelta + primer frame listo → swipe instantáneo tipo TikTok. **NO precargar el anterior** (`abs(page-current)<=1`) - compite el slot y deja a veces al siguiente sin player listo.

### 🎬 Reels v2 (ReelsFeedScreen + ReelPlayerPool) - motor nuevo desde cero (sesión 2026-09-13)
* **Decisión**: el usuario pidió reconstruir el reels "como TikTok" sin fijarse en lo existente. Se creó paquete `com.example.reels.*` con motor propio **sin tocar** `TikTokVideoFeedScreen`/`ReelDualPlayerManager` (que siguen en el repo por compat).
* **`ReelPlayerPool`** (engine): pool de `POOL_SIZE=3` ExoPlayers reutilizados, cada uno con `DefaultTrackSelector` forzando máxima resolución (`clearVideoSizeConstraints()` + `setMaxVideoSize(MAX,MAX)` + `setMaxVideoBitrate(MAX)`), `DefaultLoadControl` fast-start (MIN=10s/MAX=40s/BUFFER_FOR_PLAYBACK=300ms/POST_REBUFFER=3s/backbuffer 4s). `acquire(reelId, url, volume, stableUrl)` guarda la URL estable `vcdn://` + timestamp; `play()` refresca preventivamente si la URL tiene >45s (TTL VCDN ~60s); `handlePlayerError` detecta HTTP 401 (errorCode 2004) y refresca la URL firmada mid-playback → resuelve **issue #15**.
* **`ReelPreloadController`** (engine): preload adaptativo — decide cuántos reels precargar según velocidad de swipe (1-2) y duración (~3-8 MB por reel), usa `CdnManager.resolveMediaUrl` + `CacheDataSourceFactory.prefetchVideo` (SimpleCache), respeta offline y no satura (max 2 descargas paralelas).
* **`ReelsFeedScreen`** (ui): `VerticalPager`, cada página adquiere su player con `ensureAcquired`, overlay TikTok completo (avatar, displayName, caption, rail derecha like/favorito/comentar/compartir, **doble tap corazón**), `registerView`, pausa todos menos el activo al cambiar de página.
* **Navegación**: la ruta `tiktok/{stateId}` y el tab `clips` ahora apuntan a `ReelsFeedScreen`. `ReelsFeedScreen` NO tiene `onNavigateToLive` (el viejo sí).
* **Calidad**: máximo bitrate/resolución disponible vía track selector (límite real: VCDN sirve HLS de una sola calidad, sin ABR ladder — escalar esto es trabajo de infra VCDN, no app).
* Compilación: `source scripts/toolchain_env.sh && ./gradlew :app:compileDebugKotlin` (JDK17 .toolchain/).
 * **Fix 1 (commit `ee1fdbf`, main, v1.3.35):** se eliminó el **refresh preventivo de URL firmada VCDN** que corría cada ~50s (`setMediaItem+prepare+seekTo` sobre el player en vuelo) - eso destruía el codec hw y causaba `CodecException 4003/4006`, black flashes y cortes a mitad de reproducción. La defensa contra expiración del token (~60s real) es **solo reactiva**: al recibir `httpStatus 401` mid-stream, `onPlayerError` fuerza `resolveMediaUrlFresh` (+`refreshActiveUrl` en el manager, preservando posición/playWhenReady, limitado a 2 intentos). `refreshActiveUrl` **ya trae su propio `try/catch IllegalStateException`** (devuelve `false` si el player fue liberado/reemplazado - no crashea), así que **no necesita** la guarda `exoPlayerRef==player` que sí se usa en el retry genérico.
 * **Fix 2 (hardening retry):** el retry genérico **debe** ir guardado con `exoPlayerRef == player` (si el codec-recovery reemplazó el player entretanto, `prepare()` sobre el stale arroja `IllegalStateException 1004` → bucle de "error definitivo" falso): envolver `player.prepare()` en try/catch preservando `currentPosition`/`playWhenReady`. El camino `handleCodecError` (codec init errors) **no debe** caer al retry 401/network: se marca `isRetrying || isRecovering` y se delega al dual manager (que recrea player con renderer degrades y escalamiento descentralizado.
 * **El 401-recovery NO necesita guarda anti-stale**: `refreshActiveUrl` valida internamente (slot/player null → false), y el `finally` solo marca `hasError=true` si no se recuperó - **sin crash ni bucle**.
* **Falso «Recuperando vídeo...» (hotfix commit `53f9138`, en v1.3.36):** la causa era `isCodecInitializationError()` **demasiado amplio**: matcheaba por `errorCodeName` conteniendo "CODEC"/"DECODER" - así errores de red/IO que arrastraban resto de decoder (y `IllegalStateException` del prepare fallido en preload) se clasificaban como codec y disparaban la recreación visible del player (el cartel + recovery que a veces fallaba). **Regla**: clasificar codec SOLO por `errorCode` real (`ERROR_CODE_DECODER_INIT_FAILED`/`_QUERY_FAILED`/`ERROR_CODE_DECODING_FORMAT_UNSUPPORTED`) o `MediaCodec.CodecException` en la cadena **sin** `InvalidResponseCodeException` aguas arriba; **jamás** por el nombre del error. Además: **errores en preload = silenciosos**: `onPlayerError` con `isPreload` hace `dualManager.releaseIfOwned(state.id)` + return (sin recovery, sin cartel - el swipe re-adquiere limpio). El overlay de recovery queda **solo spinner** (sin texto «Recuperando vídeo...») para no asustar al usuario.
 * **Diagnóstico**: `diagnostics.record(DiagnosticCategory.NETWORK/ERRORS...)` con `correlationId=state.id.take(36)` es el rastro estándar para depurar reintentos y 401s en este screen.



### 📐 Reels - layout e insets (sesión 2026-09-15, commit `1afd4a6`)
* **Causa raíz de la píldora y la barra de progreso mal posicionadas**: la ventana del feed NO es edge-to-edge (`setDecorFitsSystemWindows(window, true)`), así que el sistema ya reserva el espacio de las barras; el overlay volvía a aplicar los insets (`navigationBarsPadding()` + inset superior por cutout/status) = **doble conteo**. Síntoma medido: píldora ~43dp y barra ~48dp más adentro de lo debido (barra a ~116dp del borde inferior con franja nav de solo 48dp). **Fix**: `decorFitsSystemWindows = true` explícito y **cero insets** en el overlay; posiciones con dp plano.
* **Cómo saber si la ventana es edge-to-edge desde una captura**: si las barras del sistema se ven **opacas** (color uniforme en una fila del status bar y en la franja de navegación) => `decorFits=true`, el sistema ya reservó el espacio y el overlay NO debe aplicar insets. Si el video se ve detrás de las barras => `decorFits=false` y los insets SÍ son necesarios. Aplicar los dos siempre = doble espacio.
* **Calibración de densidad en capturas**: píldora de 44dp = 112px y franja nav de 48dp = 122px => ~2.545 px/dp (1080x2460 ≈ 424x967dp). Sirve para medir posiciones sobre capturas.
* **`ReelProgressBar`**: el Box contenedor usaba `contentAlignment = Alignment.Center`, así que el relleno blanco se **centraba** (con fracción 0.15 arrancaba al ~42.7% - "empieza desde el centro"). **Regla**: `Alignment.CenterStart` para anclar relleno y thumb a la izquierda.
* **Layout resultante (referencias)**: píldora a `8dp` bajo la barra de estado; barra de progreso con `padding(bottom = 4.dp)` + Box de 20dp (rizo a ~14dp del fondo del contenido); rail e info a `bottom = 50.dp` (8dp de aire sobre el bloque tiempo+barra). El tiempo (`0:03 / 0:20`) ya no pisa el chip de hashtag.

### 🎥 Live - flujo de cámara y viewer (sesiones 2026-09-15/16, rama `kilo/live-realtime`)
* **Viewer UI (commit `310fbf8`)**: `LiveViewerScreen` renderiza header tipo card semitransparente con avatar real del host (`PanaAvatar` + `observeIdentity`), badge LIVE, stats `Espectadores:`/`Me gusta:`, chat con avatar+username bold+eventos de sistema, bottom bar y sheets (gift/requests/studio/more). Engagement **real**: migración `20260916000000_live_engagement.sql` + RPCs (like/gift/saldo con `auth.uid()`) + `LiveEngagementRealtimeManager`/`LivePresenceManager`/`LiveRealtimeManager`. Con `service_role` los RPC rechazan con `not_authenticated` (esperado). Vista `public.messages` NO publicable en Realtime; los INSERTs de mensajes SOLO con JWT del usuario autenticado (trigger impone `sender_id = auth.uid()`).
* **BUG CÁMARA YA CORREGIDO (commit `9cc864f`)**: las 3 pantallas live (`LiveBroadcastScreen`, `LiveGuestScreen`, `LiveViewerScreen`) usaban `repository: LiveRoomRepository = LiveRoomRepositoryImpl(LocalContext.current)` como default param → **se re-evaluaba en CADA recomposición de Compose**, creando instancias huérfanas de `LiveKitManager` con capturadores de cámara sin liberar ⇒ fallos intermitentes de cámara al iniciar un live ("cámara ocupada"/preview negra). **Fix**: default `null` + `val repo = repository ?: remember { LiveRoomRepositoryImpl(context) }` (instancia única por pantalla). REGLA: **nunca** instanciar managers de LiveKit/cámara como default param de Composable; usar `remember`.
* **Flujo de cámara broadcast**: permisos `CAMERA`+`RECORD_AUDIO` (manifest + launcher) → `startBroadcasting()` conecta → `localParticipant.setCameraEnabled(true)` → espera hasta 3 s (`repeat(60){delay(50)}`) el `VideoTrack` local de la publicación → si no llega, `IllegalStateException` y limpia (no sale al aire sin cámara). `LiveVideoSurface` hace `initRenderer` SOLO si `room != null` (el renderer se adjunta al track cuando éste existe); placeholder pre-inicio "La cámara se activará al iniciar" (no spinner infinito, ya que la cámara NO se enciende hasta pulsar INICIAR).
* **`Room.initVideoRenderer` existe en livekit-android 2.13.0**; `LocalVideoTrack.createCameraTrack` también existe pero usa un `startVideoTrack` estático/global del Room (API interna propensa a mutación entre instancias) → NO usar para preview pre-conexión sin evaluar bien el riesgo.
* **SPINNER INFINITO AL INICIAR TRANSMISIÓN — CAUSA Y FIX (commit `dddbff0`)**: el botón "INICIAR" quedaba cargando para siempre. **Causa raíz**: `Room.connect()` en livekit-android 2.13.0 es **suspending y NO trae timeout por defecto** — si el WebSocket de LiveKit no responde, la corrutina del botón se cuelga para siempre (spinner infinito, sin error nunca). **Fix**: `withTimeout(10s)` en `LiveKitManager.connect()/startBroadcasting()/joinAsGuest()` alrededor de `currentRoom.connect(url, token)` y `withTimeout(10s)` al poll del `VideoTrack` local (antes `repeat(60)*50ms`=3s pero el `connect()` anterior podía colgarse antes de llegar). **No relanzar TimeoutCancellationException** (cancela la corrutina del caller y evita que vea el error); capturarla como `Exception` normal. Además `withTimeout(20s)` general en el bloque del botón de `LiveBroadcastScreen` (imports explícitos `kotlinx.coroutines.withTimeout` y `CancellationException`, no aprovechar el wildcard del `rememberCoroutineScope`). El screen muestra el mensaje real de `LiveConnectionState.Error(message)` en vez del genérico.
* **REGALOS ANIMADOS TIPO TIKTOK (fallo de sesión 2026-09-16)**: el catálogo real `public.live_gifts` tiene ahora **12** gifts (migración `20260916010000_live_gifts_premium.sql` ejecutada en prod): rose(1), heart(5), applause(10), star(50), crown(200), diamond(500), rocket(1000), **galaxy(5000, 🌌), lion(3000, 🦁), tiger(3000, 🐯), airplane(2000, ✈️), submarine(2000, 🚢)**. El RPC `live_send_gift` descuenta wallet real y persiste en `live_gift_events`; el evento se distribuye por Realtime a todos los viewers (`LiveEngagementRealtimeManager` → `applyGiftEvent` → `_giftPulse`). **Motor de efectos v2** (`LiveGiftEffectsOverlay`, `ui/components/LiveGiftEffects.kt`) superpuesto al reproductor, con estilo/paleta por código (`giftStyle`): galaxy=tonos violeta/azul estelar 40 particulas, lion/tiger=dorado/naranja, airplane/submarine=azules/cián, rocket=rojo/fuego. Cuando `LiveGiftPulse.code` ∈ `FULL_SCREEN_GIFTS` reproduce: **flash de cámara** al inicio + **shockwave** (anillo expansivo 220dp) + **rayos de luz radiales** que giran + **ráfaga de partículas** de la paleta + **emoji gigante 104sp** con pulse + **banner con remitente real** (`rememberLiveIdentity`) y **contador de combo** (`xN · N combo`, mismo emisor+mismo regalo dentro de la ventana). Los regalos normales usan banner ligero con combo. **Todo real**: sin assets externos (emoji nativo), sincronizado por Realtime. `LiveGiftPulse` lleva `code: String?`; los constructores en `sendGift` y `applyGiftEvent` lo rellenan. **Wallets de prueba**: se cargaron **1.000.000 🪙** a Andrés (`4d116a2c-...`) y Gael (`0e332e11-...`) vía upsert directo en `public.user_wallets` (solo prueba, no es flujo de producción).

### 🎨 Pre-live de transmisión rediseñado (glassmorphism sobre CameraX) (sesión 2026-09-17, rama `kilo/panalink/v2-live-prelive-glass`)
* **Qué cambió**: el pre-live de `LiveBroadcastScreen` (estado `!isLiveStarted`) dejó de ser un `Column` centrado con `Scaffold`/`TopAppBar` opacos y `OutlinedTextField`. Ahora el fondo es la **preview real de CameraX a pantalla completa**, con scrim negro `alpha 0.4f` y blur, y los controles flotan encima con acabado *glassmorphism*.
* **Componentes nuevos** (2 archivos, sin assets externos):
  - `live/ui/components/LiveCameraBackground.kt`: `LiveCameraBackgroundPreview` (`PreviewView` full size, `ImplementationMode.COMPATIBLE` = TextureView, `ScaleType.FILL_CENTER`) + `LiveCameraPreviewController` (fachada con `releaseCamera()` idempotente) + `rememberLiveCameraPreviewController()`.
  - `live/ui/components/LivePreliveGlass.kt`: `LivePreliveTopBar` (back + título blanco, `statusBarsPadding`, sin TopAppBar), `LiveGlassPanel` (`RoundedCornerShape(24.dp)`, gradiente blanco 0.13→0.05, borde `0.6.dp` blanco/menta alpha 0.10), `LiveGlassTextField` (`BasicTextField` transparente sin línea inferior, placeholder alpha 0.45), `LiveGlassDivider` (separador alpha 0.08), `LiveStartBroadcastButton` (`CircleShape`, verde neón `0xFF00E676`, `shadow` 20.dp verde = glow, `navigationBarsPadding`). Constantes `PanalinkNeonGreen` / `PanalinkMint` viven aquí.
* **Scaffold sin TopAppBar**: la pantalla ya no usa `TopAppBar` en ningún estado (`topBar = {}`). Tanto el pre-live como el directo activo son edge-to-edge: cada elemento flota y gestiona sus propios insets. El `containerColor` es `Color.Transparent`; el fondo real lo pone el video.
* **⚠️ Blur real (no simulado)**: el desenfoque se aplica con `View.setRenderEffect(RenderEffect.createBlurEffect(42f, 42f, CLAMP))` sobre el `PreviewView`. Requiere **API 31+**; exige `ImplementationMode.COMPATIBLE` (TextureView), porque el `RenderEffect` no se aplica a `SurfaceView`. En APIs < 31 el fondo se ve nítido y el scrim 0.4f + panel siguen dando contraste (degradación aceptable, no hay crash).
* **⚠️ Regla de cámara (crítica, zero regresión)**: CameraX y LiveKit pelean por el sensor. El botón INICIAR llama **`cameraPreviewController.releaseCamera()` (`unbindAll()`) ANTES** de pedir el token y llamar `startLiveInBackground` → así LiveKit reclama la cámara libre y no aparece "cámara ocupada" (bug histórico `9cc864f`). La preview pre-live **no** publica track a LiveKit: es solo CameraX local. El `DisposableEffect` del componente también libera el provider al salir.
* **Validación**: `./gradlew --no-daemon :app:compileDebugKotlin` → **BUILD SUCCESSFUL** (baseline `main` también verde antes del cambio). Sin warnings nuevos en los 2 archivos añadidos ni en el modificado. `scripts/sanitize_invisible.sh` → limpio. Se restauró `scripts/toolchain_env.sh` (regla portable) antes de commitear.
* **Verificación visual sin emulador**: render PIL de la geometría en `/tmp/prelive_preview.py` (espeja los dp: status 24, icon 48, panel 24dp radius, botón 58dp/`navigationBarsPadding`) → confirma layout, sin solapes y con el botón flotante libre del panel.

### 🔴 Directo activo rediseñado (cámara inmersiva + controles flotantes) (sesión 2026-09-17, misma rama)
* **Qué cambió**: el estado `isLiveStarted` de `LiveBroadcastScreen` dejó de tener `TopAppBar` y barra inferior negra. Ahora la **capa base es el track local de LiveKit** a `fillMaxSize` (cámara real ya publicada) y todos los controles flotan encima con acabado glass.
* **Componente nuevo**: `live/ui/components/LiveLiveGlass.kt` → `LiveStatusPill` (pulso + tiempo + espectadores en una píldora glass), `LiveGlassIconButton` (icono en contenedor circular glass; `isAlert` lo pinta rojo), `LiveEndPill` (píldora roja intensa, abajo a la derecha) y `formatLiveElapsed`.
* **⚠️ NO añadir una segunda sesión de cámara en el directo**: en el estado live el video ya es el track local de LiveKit (que captura la cámara). Montar un `PreviewView`/CameraX **adicional** sobre él hace que dos sesiones peleen por el sensor → la segunda falla y el fondo se ve negro ("cámara ocupada", bug `9cc864f`). El pedido de "fondo CameraX edge-to-edge" se resuelve con **el track de LiveKit como capa base** (`LiveVideoSurface` con `backgroundColor = Color.Transparent`), que es cámara real.
* **`LiveVideoSurface.backgroundColor`**: parámetro nuevo con default `Color.Black` para no alterar los otros call sites (viewer, guest, participants). El directo pasa `Transparent` y el `Box` padre pone un respaldo `0xFF0E0E10` para que no se vea el fondo del host mientras el track aún no llega.
* **⚠️ Regresión corregida (preview negra al fallar el arranque)**: el botón INICIAR llama `releaseCamera()` **antes** de crear el stream; si `createAndStartLive` fallaba o daba timeout, se seguía en pre-live **sin cámara** y el fondo quedaba negro. Fix: `LiveCameraBackgroundPreview` acepta `restartKey` y la pantalla lo incrementa en ambas rutas de error → `LaunchedEffect` reengancha CameraX.
* **`LiveBroadcastControls` eliminado**: quedó sin usos tras mover los controles flotantes a `LiveLiveGlass.kt` (la lógica de mic/cámara/switch se conserva, ahora en `LiveGlassIconButton`).
* **Blur en el directo**: sobre un `SurfaceView` (renderer de LiveKit) el `RenderEffect` **no aplica**, así que el glass aquí es translúcido + borde fino (sin fingir blur de fondo). El blur real sigue siendo exclusivo de la preview pre-live (TextureView).
* **Validación**: `./gradlew --no-daemon :app:compileDebugKotlin` → **BUILD SUCCESSFUL**, sin warnings en los archivos tocados. `scripts/sanitize_invisible.sh` → limpio. APK BETA compilada y publicada para probar en dispositivo.

### 🧹 Lecciones de edición/tooling (sesión 2026-09-11) - aplicar SIEMPRE
* **`file_editor` corrompe strings largos con backticks** (ej. `` `versionCode` ``): el `old_str` jamás matchea y da error "did not appear verbatim". Cuando un `str_replace` falla así, **no insistir**: dumpear la línea con `od -c` o `sed -n 'Np'`, detectar bytes invisibles (U+200B = `342 200 213` en octal/`\xE2\x80\x8B`) y reemplazar con `sed -i` usando **substrings contiguas** (ej. cambiar `61\*\*`→`62\*\*` y `v1.3.34 `→`v1.3.35 ` por separado) en vez de la cadena larga completa.
* **`sed -i` con patrón que matchea en VARIAS zonas borra de más**: en esta sesión, `/Modifier.height(12.dp)/d` eliminó el Spacer del overlay «Sin conexión» ADEMÁS del «Recuperando vídeo...» (el mismo patrón en 2 sitios). **Regla**: tras CADA `sed -i`, revisar SIEMPRE el `git diff` y verificar que el patrón solo tocó la zona deseada; ante la duda, editar con ancla única (file_editor con contexto circundante).
 * **`sed` no matchea U+200B con `[[:space:]]`** - el zero-width space NO es espacio POSIX; hay que matchear el byte literal (`\xE2\x80\x8B` en GNU sed) o anclarse a substrings contiguas sin el byte..
 * **`awk 'NR>=a && NR<=b {printf ...}'` es fiable** para inspeccionar rangos (muestra el contenido tal cual, sin corromper nada). **`sed -n 'a,bp'` también** OK para lectura. **`cat -A`** para ver finales de línea; **`od -c`** para hexdump exacto.
 * **Commit messages largos**: usar `git commit -F -` con el mensaje por stdin (heredoc NO - canal sunicode; `printf '%s\n'`) o `file_editor`+`git commit -F <archivo>`. **NO** `git commit -m` con mensaje largo en el terminal - el canal corrompe el texto.
 * **Construir JSON de payload para APIs**: usar `jq -n --arg/--rawfile` con archivos (base64 a `.b64` + `--rawfile`) y `curl --data-binary @archivo` - **jamás** `jq -n` inline ni heredoc ni concatenaciones en terminal (el canal corrompe). Verificar siempre `jq empty <archivo>` antes de POST/PUT/PATCH..

## 🧼 Regla Anti-Corrupción de Bytes Invisibles (OBLIGATORIA)

Los caracteres Unicode invisibles **U+200B (zero-width space)** y **U+FEFF (BOM)** corrompen código silenciosamente: rompen heredocs, generan `SyntaxError` en Python, ensucian diffs y pueden alterar compilación. Esta sesión los detectó inyectados por el canal de generación de texto del agente. Reglas duras para TODAS las sesiones:

1. **CREAR/EDITAR código con `file_editor`, NUNCA con heredoc** (`cat > file <<'EOF'` está prohibido para código). El heredoc pasa por el canal de terminal que inyecta U+200B..
2. **Después de CADA edición, ejecutar**: `bash scripts/sanitize_invisible.sh` - limpia U+200B/U+FEFF de todos los archivos de texto del repo y reporta si había algo..
  Si reporta archivos, **no committear hasta re-verificar** que el diff siga siendo semánticamente correcto..
3. **Nunca crear scripts Python ni código con `= 0` u otros patrones numéricos inyectados** via terminal;; verificar siempre con `od -c` o `grep -rlP '\x{200B}'` cuando haya duda..
4. **Verificación de integridad antes de cada commit**: `grep -rlP '\x{200B}|\x{FEFF}' . --include='*.kt' --include='*.kts' --include='*.java' --include='*.sh' --include='*.py' --include='*.md' --include='*.json' --include='*.xml' | grep -v '/.git/' | grep -v '/build/'` debe devolver **vacío**. Si devuelve algo, sanitizar y re-inspeccionar..
5. **Herramientas de diagnóstico** (nunca en el repo): usar `/tmp` para archivos de prueba y borrarlos después;; si un archivo `/tmp` queda con U+200B, eliminarlo directamente (como `test_editor1.txt`)..
## 🔀 Modalidad de trabajo (Feature Branch + Beta + Release) - DESDE 2026-09-11

**Esta es la ÚNICA forma de trabajar de ahora en adelante.** El objetivo: **nunca molestar a los usuarios con actualizaciones a cada rato**. El equipo itera internamente y solo cuando algo queda FINO se publica release OTA para todos.

### Flujo obligatorio
1. **Trabajar SIEMPRE en una rama de feature** (ej. `kilo/fancy-bloom-c6g`) - NUNCA commitear directamente a `main` salvo releases o fixes urgentes aprobados.
2. **Probar los cambios como app BETA aislada** (package `com.panalink.app.beta`, label `PanaLink Beta`) que NO toca la app real de los usuarios ni sus datos - gracias al `applicationIdSuffix = ".beta"` + overlay debug (label) + `google-services.json` con doble client (ver script).
3. **Iterar**: el equipo corrige en la rama, el agente refresca el worktree y recompila la beta (script), el usuario/QA prueban en el teléfono, y se repite hasta que quede fino..
4. **Release SOLO cuando está fino**: se trae la rama a `main` (merge/te cherry-pick), se compila release con las credenciales,y se publica OTA (ver sección "Build & Release" y "Canal OTA" arriba).

### Script BETA (automatizado y versionado)
* **`scripts/build_beta.sh`** - compila la rama de feature como APK BETA de prueba. **El agente de turno SOLO ejecuta esto y entrega la URL**; no rehacer a mano.
* Qué hace: refresca la rama remota → recrea worktree limpio → genera `google-services.json` beta (duplica el cliente con `com.panalink.app.beta`) → aplica overlay debug (`PanaLink Beta` + `applicationIdSuffix = ".beta"`) → compila `:app:assembleDebug` con la toolchain del repo → verifica con `aapt` que sea `com.panalink.app.beta` → deja el APK en `$BETA_OUT` (def `/tmp/Panalink-BETA-apk-debug.apk`) y muestra el SHA256..
* **URL fija para el equipo/QA**: en ESTA sandbox el APK se sirve en el **puerto 12001** (el 12000 es el servidor de subida de capturas y responde 501 al APK). Ojo: el host y los puertos cambian por sesion - comprobar con `curl -sI` antes de entregar el link. Relanzar el servidor:
```bash
cd /workspace/project/PanalinkV2.0Ofiicial/.toolchain && nohup python3 serve_range.py 12001 /workspace/project/PanalinkV2.0Ofiicial/.toolchain/serve_apk >/tmp/range_srv.log 2>&1 &
```
* **`serve_range.py` (NO `python -m http.server`)**: la version de `http.server` NO soporta `Range`, y con el APK de ~93 MB la descarga del movil se cortaba (`ConnectionResetError: Connection reset by peer` en el log del server) -> el APK llegaba incompleto y Android decia **"paquete invalido"**. `serve_range.py` manda `Accept-Ranges: bytes` y responde **206**, asi el navegador del telefono **reanuda** donde quedo. Verificar con `curl -r 0-99 -w '%{http_code}'` (debe dar 206).
* **APK beta solo con ABIs ARM (desde 2026-09-15)**: `build_beta.sh` inyecta `ndk { abiFilters += listOf("arm64-v8a","armeabi-v7a") }`. Las ABIs de emulador (`x86`/`x86_64`) eran ~29 MB (31% del APK) y solo alargaban la descarga. La beta baja de ~93 MB a ~65 MB.
* **Diagnostico de "paquete invalido" (checklist)**: 1) `sha256sum` del archivo descargado vs. el compilado (si difiere = descarga corrupta/cortada); 2) `apksigner verify --print-certs` (debe ser `CN=Panalink Beta`, SHA-256 `450a76c1...`); 3) `aapt dump xmltree ... | grep extractNativeLibs` (debe ser `0xffffffff` = true); 4) `python3 -c` listando `lib/` para ver las ABIs; 5) revisar el log del servidor por `ConnectionResetError`.

* Variables de entorno del script: `BETA_BRANCH` (def `origin/kilo/fancy-bloom-c6g`), `BETA_WORKTREE` (def `/tmp/panalink_beta`), `BETA_VERSION_NAME`, `BETA_VERSION_CODE`, `BETA_OUT`.
* **El worktree es PERSISTENTE** (queda en `/tmp/panalink_beta`): entre rondas, el script hace fetch + reset --hard + re-aplica parches → la recompilación es incremental (rápida).

### Cómo se ve una ronda de trabajo (checklist para el agente)
1. Usuario/QA dice: *"ya corregí en la rama"* → correr:`bash scripts/build_beta.sh`.
2. Verificar la salida: `package: name='com.panalink.app.beta'` + `application-label:'PanaLink Beta'` + SHA256 visible.
3. Confirmar que la URL pública salga HTTP 200 y con el tamaño del APK nuevo.
 4. Avisar al equipo con la URL fija y el SHA nuevo (NO hace falta re-subir nada - el mismo link sirve el binario nuevo.de.
5. Cuando el equipo dice *"está fino"* → traer la rama a `main` + compilar release + publicar OTA paso-a-paso (sección "Build & Release").

### 🔑 Firma BETA estable (IMPORTANTE — NO regenerar ni borrar)
* **Keystore versionado**: `app/panalink-beta.keystore` (en el repo main). Firma **fija para TODAS las rondas** de la beta — asi la beta se instala encima de la anterior sin "conflicto de paquete".
* Credenciales fijas: store/alias: `panalinkbeta`; store/key password: `panalinkbeta`.
* **REGLA DURA**: NO regenerar ese keystore ni cambiarlo — si cambia la firma, los instaladores tendran `INSTALL_FAILED_UPDATE_INCOMPATIBLE` y habra que desinstalar la beta (perdiendo datos de prueba).
* El pipeline `scripts/build_beta.sh` ya lo copia al worktree y aplica `signingConfigs.create("beta")` en el gradle de la beta — no hace falta tocarlo a mano.
### Reglas extras de esta modalidad
* **NUNCA publicar OTA** una rama en progreso ni una beta como release exceto cuando el equipo confirma que está fino.
* **NUNCA instalar/toquetear** la app real de los usuarios desde la beta (la beta usa paquete aparte, con sus propios datos,y se desinstala con `adb uninstall com.panalink.app.beta` o desde Ajustes → Apps → "PanaLink Beta".)
* En main, **no queda rastro de la beta**: el script solo toca su worktree en `/tmp` y `app/build.gradle.kts`/`google-services.json` del repo NO se modifican al correr (el patch va al worktree, no al repo).

---

## 🧰 Toolbox del dueño de la sala de voz: entradas + colgantes (sesión 2026-09-16, rama `kilo/voice-room-toolbox`, commit `c541241`)

**Concepto (StarMaker)**: el dueño/admin configura una **entrada** (efecto a pantalla completa cuando alguien entra a la sala) y un **colgante** (adorno que rodea el avatar del sillón). Todo persistido de verdad en Supabase.

### Backend (migración `20260916020000_voice_room_toolbox_entrances_pendants.sql`, aplicada en prod)
* **`public.voice_room_decor`** (1 fila por sala): `room_id` PK, `entrance_code`, `pendant_code`, `updated_at`, `updated_by`. RLS: SELECT para cualquier authenticated; writes gated por RPC (admin-only).
* **`public.voice_room_entrance_events`** (log): `room_id`, `user_id`, `entrance_code`, `created_at`. RLS select/insert authenticated.
* **RPCs** (security definer con `set search_path=''`, mismo patrón que el resto de voice rooms):
  - `get_voice_room_decor(p_room_id)` → `entrance_code`, `pendant_code`, `updated_at` (cualquier miembro).
  - `set_voice_room_entrance(p_room_id, p_code)` / `set_voice_room_pendant(...)` → upsert en decor; exige `public.voice_room_is_admin`.
  - `record_voice_room_entrance(p_room_id, p_code)` → inserta evento; exige `public.voice_room_is_member`.
* **Ambas tablas están en la publicación `supabase_realtime`** (verificado con `pg_publication_tables`).
* **OJO querier de Supabase**: no acepta `if`/`do` top-level para `alter publication`; envolver en `do $$ ... end $$;` (caso real: la migración ejecutada desde el .sql falló al re-aplicar el bloque realtime — se aplicó aparte con DO).

### Flujo Android
* **`VoiceRoomToolboxCatalog.kt`**: catálogos locales immutables — `entrances` (10: sparkle, fireworks, rose, king, party, rocket, music, music2, heart, angel) y `pendants` (9: none, gold, crown, halo, hearts, music, fire, diamond, bolt). Cada spec define emoji + gradiente/paleta. Sin assets externos.
* **`VoiceRoomToolboxSheet.kt`**: `ModalBottomSheet` con pestañas Entradas/Colgantes, grid de opciones, selección marcada con check. Solo alcanzable por admin.
* **`VoiceRoomEntranceOverlay.kt`**: overlay a pantalla completa (llamarada radial giratoria, partículas en paleta del efecto, avatar+número dentro de círculo degradado, etiqueta "ENTRÓ A LA SALA"). Animación auto-termina y llama `onDone`.
  - `VoiceRoomPendant(code, size, modifier)`: anillo pulsante + símbolo arriba (👑/😇/💖/🎵/⚡ según flags) alrededor del avatar.
* **`VoiceRoomRedesignedScreen.kt`**:
  - Botón "🎛️" en la bottom bar SOLO si `isAdmin && onOpenToolbox != null`.
  - Sheet + overlay al final del composable (overlay lleno sobre todo).
  - `VoiceRoomHostSeat` y `VoiceRoomGuestSeatGrid`/`VoiceRoomSeatRow`/`VoiceRoomRedesignedSeat` aceptan `pendantCode` y dibujan el pendant en el Box del asiento.
* **`VoiceRoomViewModel.kt`**:
  - `loadRoomDecor()` (async, al abrir toolbox), `applyRoomDecor()` (suspend, en `setupRoom` sincronizado ANTES de `recordMyEntrance` para usar el code real).
  - `setEntrance`/`setPendant` optimistas + RPC; `openToolbox`/`closeToolbox`; `recordMyEntrance()` inserta evento al entrar.
  - `entranceEvent` en `VoiceRoomUiState` dispara el overlay.
  - En `onTableEvent`: rama `"voice_room_decor"` (INSERT/UPDATE → actualiza `entranceCode`/`pendantCode` en vivo) y `"voice_room_entrance_events"` (INSERT → construye `VoiceRoomEntranceEvent` y setea `entranceEvent`, solo si `uid != myId`).
* **`SupabaseVoiceRoomSignaling.kt`**: añadidas `voice_room_decor` y `voice_room_entrance_events` a la lista de `postgres_changes` joins (con filtro `room_id=eq.`).
* **Sin duplicados**: el overlay NO se dispara desde `announceJoin` (solo mensaje de sistema); la fuente del efecto es el `voice_room_entrance_events` INSERT realtime (generado por `recordMyEntrance` del que entra). Si el RPC falla, no hay overlay para ese usuario pero sí mensaje de sistema.

### Estado actual beta
* Rama: `origin/kilo/voice-room-toolbox`. Beta: `v1.3.40-beta`, code `67`, SHA `6b284539f19aab030faec9868638a0e8e7f08946620c727d479a58763184015e`, package `com.panalink.app.beta`, firma `CN=Panalink Beta`, ABIs `arm64-v8a`+`armeabi-v7a`. URL fija en host `work-2-…` puerto 12001.

### 🔧 Fix: marcos (colgantes) concéntricos con el avatar (sesión 2026-09-16, rama `kilo/premium-effects`)
* **Síntoma**: el marco aparecía desplazado ~16 dp abajo-derecha del avatar (lo rodeaba por un lado y se salía por el otro) y la etiqueta del nombre caía más abajo que en los asientos vecinos.
* **Causa**: en `VoiceRoomRedesignedSeat`/`VoiceRoomHostSeat` el `Box` del asiento crece al tamaño del colgante (`VoiceRoomPendant` usa `size * 1.6f` ≈ 86 dp vs 54 dp del avatar) y el avatar quedaba anclado en `TopStart` mientras el marco se centraba → desplazamiento `(86.4 - 54) / 2 ≈ 16 dp` en X e Y (marco no concéntrico).
* **Fix** (commit `4c868dc`): `Box(contentAlignment = Alignment.Center)` en el contenedor del asiento (sillón del anfitrión + asientos de invitados). Sin colgante el Box mide exactamente el avatar, así que el layout no cambia (zero regression); el badge "Anfitrión" y el de nivel conservan su anclaje.
* **Beta**: `v1.3.41-beta`, code `68`, SHA `77bd9623318c4bc44ddca8065cacebe89efaef6b462a783b56e91925e920f116` (misma firma `CN=Panalink Beta`, se instala encima de la anterior).

### 🔧 Colgante personal que viaja entre salas + nombres pegados al avatar (sesión 2026-09-16, rama `kilo/voice-studio-audio`, commit `820d304`)
* **Síntoma**: al entrar a OTRA sala, el colgante personal elegido con "Mi colgante" no se veía (los demás salas no lo mostraban), aunque en la propia sala sí. El usuario lo eligió para llevarlo puesto en todas las salas.
* **Causa raíz (DB, no app)**: el trigger `trg_sync_profile_to_public_profile` se creó con `AFTER INSERT OR UPDATE OF first_name, last_name, display_name, avatar_url, updated_at ON public.profiles` — **`pendant_code` NO estaba en el `UPDATE OF`**, así que un PATCH que toca SOLO `pendant_code` no disparaba el trigger y `public_profiles.pendant_code` nunca se actualizaba. Todas las salas leen `public_profiles` → el colgante personal nunca aparecía fuera de la propia sala. El BEFORE trigger `trg_profiles_updated_at` cambia `updated_at` vía `NEW.updated_at`, pero eso NO activa un trigger `UPDATE OF updated_at` (la lista de columnas es la del `SET` del UPDATE, no las columnas que el trigger modifica).
* **Fix**: reconstruir el trigger incluyendo `pendant_code` en el `UPDATE OF`:
  ```sql
  drop trigger if exists trg_sync_profile_to_public_profile on public.profiles;
  create trigger trg_sync_profile_to_public_profile
  after insert or update of first_name, last_name, display_name, avatar_url, pendant_code, updated_at
  on public.profiles for each row execute function public.sync_profile_to_public_profile();
  ```
  Aplicado en prod y en `supabase/migrations/20260916100000_profile_pendant_code.sql`. Verificado: PATCH `profiles.pendant_code='gold'` → `public_profiles` queda `gold` (antes quedaba `none`). Luego backfill idempotente `update public_profiles set pendant_code=profiles.pendant_code ...`.
* **Validación empírica**: probar con service_role sobre un usuario de prueba y RESTAURAR el valor original después. La migración backfill del trigger previo ya copió los valores (para que la propagación funcione hay que re-correr el backfill tras recrear el trigger).
* **Nombres separados del avatar**: el slot fijo era `54dp * SeatSlotScale(1.85)` ≈ 100dp con avatar de 54dp centrado → 23dp de zona muerta abajo + Spacer 5-6dp → nombre a ~28dp bajo el avatar. **Fix**: `SeatSlotScale 1.85→1.75` (sigue > overflowScale 1.72, margen para el marco) + Spacer del anfitrión 6→2dp y del invitado 5→2dp → nombre ~6-7dp más arriba en TODOS los asientos por igual. El marco vectorial (92.9dp) sigue cabiendo en el slot (94.5dp) y no tapa el nombre.
* **Ojo**: los marcos RASTER (`overflowScale` 2.0-2.6) siguen desbordando el slot y su arte inferior ya convive/cubre ligeramente el nombre — limitación conocida (ver sección raster); no se agravó de forma material.
* **Beta**: `v1.3.42-beta`, code `69`, SHA `5e46db62da5ff76891a178c229be48e97af6f1f765682472abaac9fb1437a22a`, package `com.panalink.app.beta`, firma `CN=Panalink Beta`, ABIs `arm64-v8a`+`armeabi-v7a`.

#### 🚨 Descubrimiento clave: la función NO está en `main` (sesión 2026-09-16 nocturna, commit `6875fd4`)
* **El usuario reportó "veo eso igual"**. La causa de fondo: `main` NO tiene la función "Mi colgante" — `git merge-base --is-ancestor 5e59328 main` da falso. `main` no tiene `VoiceRoomMyPendantSheet`, `setMyPendant`, ni `SeatSlotScale` (su `VoiceRoomRedesignedScreen.kt` es anterior). **Si el usuario prueba en la app de PRODUCCIÓN (OTA desde main), es imposible que guarde colgantes o que los nombres cambien.** La función solo vive en `kilo/voice-studio-audio` (5 commits sobre main, fast-forwardable).
* **El flujo DB end-to-end está VERIFICADO** (con usuario throwaway `pendtest@panalink.app`, creado vía `/auth/v1/signup`, email confirmado con `update auth.users set email_confirmed_at=now()`, login con `/auth/v1/token?grant_type=password`, luego borrado con `delete from auth.users where id=...`):
  - PATCH `profiles?id=eq.<uid>` body `{"pendant_code":"gold"}` con JWT authenticado → **204**.
  - `profiles.pendant_code` = `gold` ✅ y `public_profiles.pendant_code` = `gold` automáticamente (trigger ya propaga) ✅.
  - La BD de staging solo tiene 9 usuarios y TODOS con `pendant_code='none'` → nadie ha guardado colgante aún.
* **Los triggers de profiles NO bloquean el PATCH de `pendant_code`**: `block_identity_field_updates` solo protege id/pin/pin_hash/qr_payload; `set_last_profile_edit` no cuenta pendant_code (correcto, no infla profile_edit_count).
* **Fix visual FUERTE de nombres** (en vez del ajuste de 3dp de `820d304`, que era imperceptible): `VoiceRoomSeatNameChip` — el nombre ahora es un overlay **dentro** del Box del slot (`align(Center).offset(y = avatarSize/2 + 2.dp)`) con chip `Color(0x990B1220)` + `RoundedCornerShape(6.dp)`, en vez de Text debajo del slot. Antes quedaba a ~22dp bajo el avatar (slot 94.5 centrado); ahora queda a 2dp del borde del badge y el marco nunca lo tapa (el texto se dibuja encima del material del marco con su chip). Aplicado a `VoiceRoomHostSeat` y `VoiceRoomRedesignedSeat`. El "NO. N" del sillón vacío va al `BottomCenter` del slot. **Cuidado**: `Modifier.align` solo existe dentro de `BoxScope` → el chip NO puede usar `align` internamente; el caller le pasa `chipModifier` ya posicionado.
* **`setMyPendant` robusto**: si el PATCH responde 401/403 → `SessionManager.refreshSession()` y reintenta con el token nuevo (mismo patrón que `ProfilesRepository.updateProfile`). Antes fallaba silencioso con token vencido (el usuario elegía colgante, se cerraba el sheet y nunca se guardaba).
* **Ajuste de altura del nombre (commit `011e060`)**: el usuario reportó el chip "casi montado" con gap 2dp → se sube a **gap 6dp** (`offset = avatarSize/2 + 6.dp`) en anfitrión e invitados. Referencia geométrica usada: el anillo del marco vectorial (overflowScale 1.72, default) cierra a `~5.5dp` bajo el borde del avatar (`0.70*rFrame - avatar/2`), así el chip queda justo bajo el anillo del colgante sin ser tapado y sin montarse en el sillón. Para MÁS bajada futura: `gap 8-10dp` empieza a rozar los colgantes raster (sobresalen 27-44dp bajo el avatar).
* **Beta**: `v1.3.44-beta`, code `71`, SHA `12f15371c727389c102f99ff3801f6e54d158ef5efebd469acb4c06d3e4ff919`, package `com.panalink.app.beta`, firma `CN=Panalink Beta`, ABIs `arm64-v8a`+`armeabi-v7a`.
* **Beta anterior**: `v1.3.43-beta`, code `70`, SHA `b3256716c092d85feae7f4111255574b23b7fb410a426edf58d534df805f46a8`. URLs activas (hosts de esta sesión, confirmar con curl -sI; sustituir el nombre de archivo por la versión vigente):
  - `https://work-2-raisotzdcnuptyzh.prod-runtime.all-hands.dev/Panalink-BETA-v1.3.44-code71.apk` (puerto 12001, serve_range.py).
  - `https://work-1-raisotzdcnuptyzh.prod-runtime.all-hands.dev/apk/Panalink-BETA-v1.3.44-code71.apk` (puerto 12000, capture_server.py).
  - Servidores: seguir vivos desde sesiones previas (`ps aux | grep serve_range|capture_server`). Si se relanzan con `setsid nohup ... < /dev/null &` y dan `Address already in use`, es que ya hay una instancia viva → no relanzar.
* **Próximo paso para producción**: cuando el usuario dé el OK tras probar la beta, fast-forward `main` (hoy `bb5ff39` < `6875fd4`, `branch..main=0`): `git merge --ff-only origin/kilo/voice-studio-audio` y publicar OTA (main hoy NO tiene la función, así que los usuarios de producción NO pueden usar colgantes hasta ese release).

### 📥 Entrega del APK al teléfono (anti "paquete no válido")
* **Servir SIEMPRE con nombre versionado** (ej. `Panalink-BETA-v1.3.41-code68.apk`), nunca reusar `Panalink-BETA-apk-debug.apk`: el móvil guarda el parcial/descarga previa con el mismo nombre y al "reanudar" mezcla bytes de dos builds distintos → Android dice **paquete no válido**.
* Cabeceras obligatorias: `Accept-Ranges: bytes` (206), `Content-Length` exacto y `Cache-Control: no-store`.
* **Dos URLs independientes** para descartar cortes del ingress:
  - puerto **12001**: `serve_range.py` sirviendo `/workspace/.../.toolchain/serve_apk/`.
  - puerto **12000** (servidor de capturas, `upload_server.py`): ruta extra `/apk/<archivo>` con el mismo soporte de Range.
* Diagnóstico del APK servido (debe pasar TODO antes de entregarlo): `sha256sum` local == descargado, `zipalign -c -v 4` → "Verification succesful", `apksigner verify` (v2 ok, `CN=Panalink Beta`), `aapt dump xmltree | grep extractNativeLibs` → `0xffffffff`, y `lib/` con `arm64-v8a`+`armeabi-v7a`.
* Si aun así falla en el móvil: **desinstalar "PanaLink Beta" antes de instalar** (un conflicto de firma se reporta en muchas ROMs como "paquete no válido") y confirmar que el tamaño del archivo descargado es exactamente el del compilado (bytes, no "MB" redondeados).
* **CAUSA MAS FRECUENTE de "paquete no valido": DOWNGRADE de `versionCode`.** Si el telefono ya tiene una beta anterior instalada y la nueva se compila con un `BETA_VERSION_CODE` **MENOR**, Android 14+ la rechaza con *"App not installed as package appears to be invalid"* — el usuario lo reporta identico a un APK corrupto. Caso real: beta compilada con code `63` cuando el default del script es `66` (habia una 66 instalada) -> rechazo. **Regla**: usar siempre un code **mayor** que la ultima beta instalada (no bajar de `66`); verificar con `aapt dump badging | grep ^package` ANTES de publicar. **Descarte rapido**: un APK con zip integro (`zipfile.testzip()` = None) + `apksigner verify` v2 OK + sha256 del servido == compilado **no puede** ser un problema de descarga -> mirar `versionCode`/firma primero. Ojo: `Verified using v1 scheme: false` **no** es un problema (produccion tambien es v2-only; ambas `minSdk 24`).
* **Un log que silencia `BrokenPipe/ConnectionResetError` NO prueba nada** si ademas escribe la linea del 200 *antes* de enviar el body. Hay que loguear los bytes realmente entregados (`enviado=N/total COMPLETO|CORTADO`) para distinguir una descarga cortada de una completa.

---

## 🖼️ Motor de marcos de avatar (colgantes) - `com.example.effects` (sesion 2026-09-16)

### Que reemplazo
Los colgantes viejos eran **dos cosas superpuestas**: el `PremiumEffectView` (aro fino + chispas) **y** un badge circular con emoji en `Alignment.TopCenter`. El emoji tapaba la parte alta del avatar y el aro era demasiado sutil. Ahora `VoiceRoomPendant` **delega en `AvatarFrameView`** y no queda ningun emoji encima.

### Archivos
* `app/src/main/java/com/example/effects/AvatarFrameSpec.kt` — `AvatarFrameSpec` + `AvatarFrameCatalog.frames` (21 modelos: 15 vectoriales y 6 raster Panalink `panama`, `cafe`, `canal`, `fiesta`, `herencia`, `tesoro`). Los 8 codigos viejos se conservan -> **el decor ya guardado en Supabase sigue resolviendo**. `AvatarFrameCatalog.byCode(null|"none"|desconocido) = null`.
* `app/src/main/java/com/example/effects/AvatarFrameView.kt` — `Canvas` procedimental. Anatomia (todo relativo a `rFrame = lado/2`):
  * `rAvatar = rFrame / overflowScale` -> hueco del avatar **transparente** (el asiento pinta la foto debajo).
  * banda `[rAvatar, rAvatar + rFrame*bandWidth]` con `sweepGradient` + gemas.
  * fruncido de petalos `[banda, rFrame*petalOuterRatio]`, atenuado por `spec.frill`.
  * ornamentos (`FrameOrnament`): CROWN, WINGS, FLAMES, HALO, BOLTS, LEAVES, FEATHERS, STARS, SPIKES, BUBBLES, HEARTS.
* **Sin distorsion**: toda la geometria se deriva del lado del lienzo -> escala a cualquier densidad sin reescalar bitmaps. El hueco central nunca se pinta: el marco solo anade material ALREDEDOR de la cara.

### Colgantes RASTER (arte Panalink) - `bitmapRes` (sesion 2026-09-16, commit `204e7f7`)
El motor tambien soporta **arte raster**: si `AvatarFrameSpec.bitmapRes != 0`, `AvatarFrameView` dibuja el PNG (`FilterQuality.High`) en lugar de las primitivas, sin rotar ni petalos. Assets en `app/src/main/res/drawable-nodpi/frame_*.png` (`panama`, `cafe`, `canal`, `fiesta`, `herencia`, `tesoro`, ~1.4 MB). Sin migracion: `pendant_code` es `text` libre en `voice_room_decor` y `profiles`.

Receta para convertir una captura de mockup en colgante (script efimero en `/tmp`, no versionado):
1. **Keying**: cada diseno trae el arte sobre un plato oscuro uniforme (~RGB 28,28,44). Distancia de color + quedarse con el **componente mas grande** (si no, el glow/estrellas sueltos entran al PNG).
2. **Hueco del avatar**: NO es uniforme entre disenos (circulo gris, bandera, gradiente, cielo) ni es una elipse perfecta. La deteccion automatica falla (el centroide se sesga con la contaminacion; la caminata radial se rompe con contraste interno). Lo que funciona es el **montaje visual 1:1**: recortar cada candidato y pintarle el circulo encima, y validar despues con avatares de prueba (`preview*.png`).
3. **Recortar con margen +10%** sobre la elipse medida: el arte AI no es simetrico y un recorte justo deja una cresta gris sobre la foto.
4. `overflowScale = (semiancho del canvas) / (radio del hueco con margen)`: ese valor hace que un avatar de diametro D caiga exacto dentro del hueco.

* **Ojo con el tamano en el sillon**: estos badges (`overflowScale` 2.0-2.6) miden mas que `SeatSlotScale` (1.85), asi que **desbordan el slot fijo** y pueden pisar nombre y asientos vecinos. En el selector del toolbox se escalan a 58dp via `pendantPreviewAvatar()`. Para que quepan en el slot habria que recortar el arte (quitar el banner de texto).

### ⚠️ Invariante duro (romperlo = marco recortado)
El lienzo es cuadrado y **todo lo que se dibuja fuera de `rFrame` se recorta**. Con `overflowScale = 1.72f` y `bandWidth` ~`0.115f` el aro cierra en ~`0.70*rFrame` -> quedan ~`0.30*rFrame` de radio libre para coronas/alas/plumas/llamas.
* Corona: arranca en `bandOuter` y mide **maximo** `0.26*rFrame` de alto.
* Ornamentos radiales (llamas, picos, plumas, rayos): empiezan en `bandOuter` (~`0.98f`), **jamas en `rAvatar`** (cruzan el anillo y entran en el hueco de la cara).
* Destellos: `rAvatar + (rFrame - rAvatar) * (0.15f + rnd*0.80f)`, nunca `rAvatar + rFrame*(0.10f + rnd*0.75f)` (se sale del lienzo).
* `frill = 0f` en los marcos cuyo ornamento principal YA es un anillo completo (llamas, picos, plumas); con `1f` se ven dos anillos superpuestos y queda sucio.

### Como verificar SIN emulador
Portar la matematica del `Canvas` a un render PIL (`/tmp/frame_preview.py`, efimero): mismos radios/angulos/beziers, supersampling y **recorte a los limites del lienzo** (eso es justo lo que delata una corona cortada). Genera el grid de los 15 + detalles. **No** es codigo de produccion: si cambia la geometria del Kotlin hay que espejarla en el script.

### 📥 Servidor de capturas (puerto 12000) - `capture_server.py`
Equivalente al `upload_server.py` de la seccion de entrega; esta sesion lo recreo como `.toolchain/capture_server.py` (el sandbox no lo tenia):
* `python3 capture_server.py 12000 <dir-apks>` (NOTA sesion 2026-09-16: Python 3.13 **elimino `cgi`**, asi que la version nueva **solo sirve APK** en `/apk/<archivo>`, sin el `<input type=file>` de capturas. Si se necesita subir capturas, usar `upload_server.py` o reconstruir el multipart a mano).
* Ruta `GET /apk/<archivo>` con `Accept-Ranges: bytes` (206) -> **segunda URL independiente** para el APK, en otro puerto, para descartar que el corte venga del ingress de 12001.
* Loguea `enviado=N/total COMPLETO|CORTADO` por archivo; un `CORTADO` es la pista directa de "paquete no valido" por descarga trunca.
* **Trampa**: los servidores lanzados con `nohup` a secas **morian al resetearse la sesion tmux** (el ingress devolvia 502). Lanzarlos con `setsid nohup ... < /dev/null &` y **re-verificar con `curl` antes de entregar cualquier link**.
* **Trampa 2 (sesion 2026-09-16)**: en Python 3.13 no usar `from http.server import SimpleHTTPRequestHandler` y sobrescribir `send_head` para devolver `(f, total)` — el `do_HEAD` heredado crashea con `'tuple' object has no attribute 'close'`. Sobrescribir tambien `do_HEAD` (cerrar el fd) usando un helper `open_and_headers()` comun.
* **Sintoma de servidor caido**: `curl -sI https://work-…-host/` devuelve **HTTP 502**. Antes de dar un link, comprobar que el puerto responde y que `ss -tlnp | grep <puerto>` muestra el proceso.

### 🔀 Dos sesiones en la MISMA rama: integrar, nunca forzar
Paso de verdad: dos sesiones trabajaron `kilo/premium-effects` desde el mismo base `adf18a2` y **divergieron** (una hizo el motor de marcos, la otra el fix de concentricidad + su beta). El remoto se movio mientras la local seguia en el commit viejo.
* **NUNCA `--force`** para "hacer que entre" el commit propio: borra el trabajo de la otra sesion. Antes de pushear: `git fetch origin <rama>` + `git log --oneline --left-right <local>...<remoto>`.
* **Rebase** cuando los conjuntos de archivos no se solapan (aqui: `effects/*` + toolboxes vs. `VoiceRoomRedesignedScreen.kt`) -> el push queda **fast-forward** y no se pierde nada.
* Los commits y ramas **sobreviven** al reset de `/tmp` (viven en el `.git` del repo principal): recuperables con `git log --all` / `git cat-file -t <sha>`. El **worktree** si desaparece -> `git worktree prune` + `git worktree add`.

---

## 🌳 Consolidacion en `main`: las 3 ramas eran lineales (sesion 2026-09-16)

`main f2394f8 -> kilo/voice-room-toolbox -> kilo/live-realtime -> kilo/premium-effects (78431b2)`: cada rama era **ancestro** de la siguiente, asi que consolidar en `main` fue un **fast-forward sin conflictos** (nada que resolver).
* Verificacion previa (obligatoria antes de consolidar): `git merge-base --is-ancestor <A> <B>` para cada par, y con la API de GitHub que **no hubiera PRs abiertos** (`/pulls?state=open` -> NINGUNO; borrar una rama con PR abierto lo cerraria).
* `git merge --ff-only origin/kilo/premium-effects` desde `main` -> un solo salto, historia lineal, cero riesgo de perder trabajo. **Usar `--ff-only`**: si NO fuera fast-forward, el comando falla en vez de inventar un merge silencioso.
* Las ramas `kilo/voice-room-toolbox`, `kilo/live-realtime` y `kilo/premium-effects` se **borraron** (local y remoto) por ser ancestros de `main` -> ningun commit se perdio.

### 🧨 `scripts/toolchain_env.sh` es PORTABLE - NUNCA commitear la version absoluta
El archivo versionado resuelve la toolchain **relativa al repo** (`_TC_ROOT="$(cd ...)/.toolchain"`). `setup_toolchain.sh` lo **regenera con rutas absolutas de la sandbox**, y ese diff aparece como modificacion local en `main`.
* **Regla**: `git checkout -- scripts/toolchain_env.sh` antes de commitear/pushear. Commitear las rutas absolutas rompe el build en cualquier otra maquina.
* La version portable funciona igual en la sandbox (resuelve a las mismas rutas; `GRADLE_USER_HOME` portable = `.toolchain/.gradle-home`, que existe), asi que no hay razon para conservar la absoluta.

---

## 🎥 Setup pre-live "Transmitir en Vivo" rediseñado (sesion 2026-09-17)

Refactor visual de la pantalla previa al directo (`LiveBroadcastScreen`) al estilo glassmorphism del mockup aprobado: fondo de camara edge-to-edge con blur + oscurecido, barra superior flotante, panel central translucido con borde fino y CTA en pildora neon con glow.

### Archivos
* **NUEVO** `com/example/live/ui/components/LiveCameraPreviewBackground.kt`: preview real de **CameraX** a pantalla completa (`PreviewView` + `Preview` use case) para el fondo. Usa `ImplementationMode.COMPATIBLE` (TextureView, no SurfaceView) porque es lo unico que permite aplicar el **blur** de Compose sobre la preview. `active=false` desvincula (`unbindAll()`); el `AndroidView` **nunca se desmonta** (solo `alpha 0/1`) porque reinsertar la misma `PreviewView` revienta con "child already has a parent".
* **NUEVO** `com/example/live/ui/components/LiveBroadcastSetup.kt`: layout completo pre-live. `LiveBroadcastSetup()` (pantalla), `SetupTopBar`, `GlassFormPanel`, `PermissionGlassPanel`, `GlassFieldGroup`, `GlassTextField` (`BasicTextField` transparente, sin lineas inferiores) y `NeonPillButton` (pildora `CircleShape` + halo desenfocado + `shadow` con `ambientColor/spotColor`).
* **MODIFICADO** `com/example/live/ui/screen/LiveBroadcastScreen.kt`: la rama de configuracion se movio a `LiveBroadcastSetup` con **early return**; el `Scaffold` (TopAppBar + preview LiveKit + comentarios + controles) queda **solo** para el estado en vivo, intacto. El `AlertDialog` de finalizar se extrajo a `EndLiveDialog(onDismiss, onConfirm)` para reusarlo en ambos estados. La rama de permisos ahora vive dentro del panel glass.

### Paleta y medidas (muestreadas del mockup)
* Verde neon de marca: **`Color(0xFF5CF8B0)`** (muestreado del boton del mockup); texto del CTA en `Color(0xFF03140C)`.
* Panel: radio **24.dp**, padding 20.dp, fondo `Brush.verticalGradient(white 0.14 -> 0.06)`, borde 1.dp `white 0.32 -> NeonMint 0.16`.
* Campos: radio 14.dp, borde `white 0.20`, fondo `white 0.05`, placeholder `white 0.42`; alturas 52.dp (titulo) y 140.dp (descripcion).
* CTA: altura 50.dp, `contentPadding` horizontal 30.dp, halo `190x46.dp` con `blur(26.dp)`.
* Fondo: preview con `blur(26.dp).scale(1.08f)` (el `scale` tapa el borde difuminado) + `Color.Black.copy(alpha = 0.4f)` + degradado vertical para legibilidad.

### ⚠️ Insets: NO usar `statusBarsPadding()`/`navigationBarsPadding()` aqui
Aunque el pedido los mencionaba, en esta app la ventana **NO es edge-to-edge** (`MainActivity.onResume` fuerza `setDecorFitsSystemWindows(window, true)`), asi que el sistema ya reserva el espacio de las barras y volver a aplicar los insets **duplicaba** el desplazamiento (misma conclusion medida en el fix del feed de reels, commit `1afd4a6`). La top bar y el CTA se posicionan con **dp plano**. Misma razon por la que tampoco se tocan las flags de la ventana ni el color de iconos de la barra de estado.

### 🔒 Regresion evitada: la camara es un recurso unico
LiveKit abre la camara en `localParticipant.setCameraEnabled(true)` dentro de `startBroadcasting()`. Si CameraX la tiene tomada, el directo falla ("camara ocupada") -> regresion directa del fix del commit `9cc864f`.
* **Solucion**: `beginBroadcast()` pone `cameraPreviewActive = false`, espera `CAMERA_RELEASE_DELAY_MS = 350L` y **recien despues** crea el stream; si falla, restaura la preview (`cameraPreviewActive = true`). El `DisposableEffect` de la preview libera igual al desmontarse (red de seguridad).

### Validacion
* `source scripts/toolchain_env.sh && ./gradlew --no-daemon :app:compileDebugKotlin` -> **BUILD SUCCESSFUL**, **0 errores** y **0 warnings** en los archivos nuevos/modificados.
* Toolchain en esta sesion: `bash scripts/setup_toolchain.sh` (idempotente, instala JDK 17 + Android SDK en `.toolchain/`).
* `gradle.properties` trae un `systemProp.javax.net.ssl.trustStore` con **ruta absoluta de otra sesion** (commiteada): Gradle avisa "trust store ... does not exist" pero resuelve dependencias igual. Si algun dia falla el SSL, ese es el primer sospechoso.

---

## 🖼️ Listado de transmisiones `Panalink Live` estilo glassmorphism (sesion 2026-09-17)

Refactor de `LiveFeedScreen` + `LiveCard` al mockup de tarjetas flotantes: fondo con resplandores neon, tarjetas de cristal sobre la miniatura del video, badges flotantes y FAB con glow.

### Archivos
* **NUEVO** `com/example/live/ui/LiveTheme.kt`: tokens compartidos del modulo Live. `LiveNeon` (#5CF8B0), `LiveOnNeon` (#03140C), `LiveNightBase` (#040A15), `LiveLiveRed`/`LiveLiveGlow`, `LiveGlassFill` (white 0.06), `LiveGlassBorder` (white 0.10), `LiveBadgeFill`, `LiveCardScrim` y `LiveCardShape` (24.dp).
* **MODIFICADO** `LiveBroadcastSetup.kt`: dejo de duplicar `NeonMint`/`OnNeonMint`/`PanelShape` y ahora consume `LiveNeon`/`LiveOnNeon`/`LiveCardShape` de `LiveTheme.kt`. Antes de reemplazar masivamente hay que **renombrar `OnNeonMint` PRIMERO** (`NeonMint` es substring de `OnNeonMint`; al reves queda `OnLiveNeon`).
* **REESCRITO** `com/example/live/ui/components/LiveCard.kt`: `Card` con `elevation = 0.dp`, `containerColor = Color.Transparent` y `BorderStroke(1.dp, LiveGlassBorder)`; dentro, la miniatura con `blur(16.dp).scale(1.12f)` + capa negra en degradado; badges `LiveNowBadge`/`LiveViewerBadge`; titulo 21.sp bold; descripcion opcional 13.sp; fila de host con `PanaAvatar` (34.dp, anillo `LiveNeon`) + `@nombre`.
* **REESCRITO** `com/example/live/ui/components/LiveSkeletonCard.kt`: mismo alto/esquinas/borde que `LiveCard` con barras pulsantes de cristal (antes era un bloque `#1F2C34` con shimmer). Compartir el alto evita el salto de layout al llegar los datos.
* **REESCRITO** `com/example/live/ui/screen/LiveFeedScreen.kt`: `Box` con `drawBehind` pintando 2 `Brush.radialGradient` neon (arriba-derecha alpha 0.34, abajo-izquierda 0.16) sobre `LiveNightBase`; `Scaffold` transparente con `CenterAlignedTopAppBar` (el titulo va centrado como el mockup); `LazyColumn` con `Arrangement.spacedBy(16.dp)`; estado vacio en panel de cristal; `LiveBroadcastFab` con halo difuminado + `Modifier.shadow` de color.

### Detalles que importan
* **El blur del badge NO es backdrop-blur**: Compose no expone blur del fondo (solo `Modifier.blur`, que difumina el propio nodo). El "glass" se logra con relleno oscuro translucido (`LiveBadgeFill`) + borde fino claro + el blur aplicado a la miniatura de atras. No existe `Modifier` de backdrop blur en la version actual de Compose.
* **`Modifier.blur` es no-op por debajo de API 31** (minSdk del proyecto = 24). En Android 12+ se ve el desenfoque; en 7-11 quedan la capa oscura y los bordes translucidos. El halo del FAB es del mismo tamano que el FAB (64.dp), asi que sin blur queda tapado por el boton en vez de verse como un disco solido.
* **`Spacer(Modifier.weight(1f))`** entre los badges y el titulo: las tarjetas son de alto fijo (`178.dp`) y el espacio sobrante se lo come el spacer, asi que con o sin descripcion el bloque inferior queda pegado al borde.
* **Contraste del texto**: la capa negra va de 0.30 -> 0.18 -> 0.72 en vertical; sin ese scrim el titulo blanco se pierde sobre miniaturas claras.
* **El icono del FAB se mantiene `Videocam`** (el mockup dibuja una camara de fotos, pero la accion es "transmitir en vivo": cambiar el icono a uno de foto seria una regresion de significado).
* **Padding inferior del listado = `FabSize + 48.dp`** para que el FAB no tape la ultima tarjeta.
* **Bug arreglado de paso**: el estado vacio antes se decidia con `uiState.activeLives.isEmpty()` (sin filtrar por `status == "LIVE"`), asi que con solo streams finalizados se veia una lista en blanco en vez del panel. Ahora el filtro se calcula una vez en `liveStreams` y alimenta tanto la lista como el estado vacio.
* **ArrowBack**: se migro a `Icons.AutoMirrored.Rounded/Filled.ArrowBack` en `LiveFeedScreen` y `LiveBroadcastScreen` (el `Icons.Default/Rounded.ArrowBack` esta deprecado y sale warning de compilacion).

---

## 🔴 Directo del anfitrion estilo glassmorphism (sesion 2026-09-17)

Rediseno del estado "directo activo" de `LiveBroadcastScreen`: se eliminaron el `Scaffold` y el `TopAppBar` solidos (`#161618`) y todo pasa a flotar sobre el video de camara.

### Archivos
* **NUEVO** `com/example/live/ui/components/LiveBroadcastHud.kt`: `LiveStatusPill` (REC + tiempo + ojo + espectadores), `LiveGlassCircleButton` (herramienta circular de cristal con estado `alert` y punto verde de encendido) y `GlowDot` (punto con halo dibujado en `Canvas`). Tambien `viewerLabel()` que singulariza "1 Espectador".
* **REESCRITO** `LiveBroadcastControls.kt`: fila flotante de 4 circulos (mic / camara / invertir / comentarios) + `EndLiveButton` (pildora roja con halo difuminado, sombra de color y destello de 4 puntas dibujado con `Path`).
* **MODIFICADO** `LiveGuestControls.kt`: el boton pasa de `Button` relleno a `OutlinedButton` con `BorderStroke(1.5.dp, LiveNeon)` + `containerColor = LiveHudFill`.
* **MODIFICADO** `LiveBroadcastScreen.kt`: `Scaffold`/`TopAppBar` fuera; `Box` con el video como capa base y HUD superior, PiP de co-host, comentarios, aviso de error y HUD inferior flotando.
* **Tokens nuevos** en `LiveTheme.kt`: `LiveHudFill`, `LiveHudBorder`, `LiveEndRed`.

### Geometria verificada contra el mockup (768x1376, ~1.868 px/dp)
Se dibujaron las cajas calculadas sobre la captura para confirmar alineacion y ausencia de colisiones entre comentarios / PiP / HUD inferior.
* Pildora de estado: top a `14dp` del borde del contenido -> 71px medidos contra 73px del mockup.
* Fila inferior: botones terminan a `14dp` sobre el borde del contenido -> 62dp desde el borde fisico contra 62.6dp medidos.
* Centro-a-centro de los circulos: 60dp (mockup 58-66dp). Boton de invitar: top 187px vs 190px medidos.

### Decisiones que importan
* **NO se aplico `statusBarsPadding()`/`navigationBarsPadding()`** aunque el pedido los mencionaba: `MainActivity.onResume` fuerza `setDecorFitsSystemWindows(true)`, asi que el sistema YA reserva el espacio de las barras y volver a aplicar los insets lo duplica (mismo bug documentado en el fix de reels `1afd4a6`: 43dp y 48dp de mas). El mockup es consistente con dp plano: sus 62.6dp libres abajo = 48dp de barra de navegacion + 14dp de margen.
* **La capa base es `LiveVideoSurface` (track local de LiveKit), NO `PreviewView` de CameraX.** Durante el directo la camara la tiene LiveKit: montar CameraX a la vez reproduce el fallo "camara ocupada" del commit `9cc864f`. El track local ES la vista de camara, a `fillMaxSize()`, y `LiveVideoSurface` ya usa `SCALE_ASPECT_FILL` (sin barras negras).
* **`Modifier.blur` no difumina el fondo**: Compose solo difumina el nodo propio. El look "cristal" del HUD se logra con relleno oscuro translucido + borde claro fino. Ademas `blur` es no-op por debajo de API 31 (minSdk 24).
* **El halo del boton FINALIZAR usa `matchParentSize()`**: asi mide EXACTAMENTE lo que el boton. En API < 31 (sin blur) queda oculto detras del boton en vez de asomar como una franja roja dura alrededor.
* **El halo va FUERA del `clip(CircleShape)` del boton**: si estuviera dentro, el clip recortaria el resplandor hacia afuera. Por eso es un hermano dibujado antes del boton, no un `drawBehind` del propio boton.
* **"REC" y no "EN VIVO"**: el mockup rotula la pildora del anfitrion con REC (es su propia pantalla de emision). El tiempo se movio de la barra inferior a la pildora superior.
* **El boton de comentarios del HUD es un toggle** (`commentsVisible`, por defecto `true`): oculta/muestra la capa de comentarios. El mockup dibuja ese 4to circulo pero el pedido solo listaba 3 herramientas; se implemento con accion real en vez de dejarlo decorativo, y por defecto no cambia el comportamiento previo.
* **El PiP del Co-Host se movio de abajo-derecha a arriba-derecha** (debajo del boton de salir): abajo chocaba con FINALIZAR y con los comentarios.
* **Comentarios anclados abajo con `heightIn(max = 240.dp)`**: crecen hacia arriba y quedan despejados de la franja de controles (`CommentsBottomPadding = 84.dp`).
* **Icono Home con la accion previa**: el mockup usa una casa arriba a la derecha; se mantuvo el comportamiento que tenia la flecha de atras (`showEndConfirmation`), que es un flujo real existente. Mapear la casa a "minimizar" habria inventado una funcion que no existe.

### Validacion
* `source scripts/toolchain_env.sh && ./gradlew --no-daemon :app:compileDebugKotlin` -> **BUILD SUCCESSFUL**, **0 errores**, **0 warnings** (se quitaron imports sin uso y el `@OptIn(ExperimentalMaterial3Api::class)` que quedo huerfano).

### 📥 Beta publicada (2026-09-17)
* Rama: `origin/kilo/live-glassmorphism` (commit `5d6583a`), incluye AMBOS redisenos (listado + directo) y el setup pre-live.
* Beta: `v1.3.46-beta`, code **73** (v1.3.45/code72 quedo obsoleta: pantalla negra), package `com.panalink.app.beta`, label `PanaLink Beta`, firma `CN=Panalink Beta` (SHA-256 `450a76c1...`, la estable: se instala encima de la beta previa sin desinstalar).
* SHA-256 del APK: `a0e9b5da3af9fd64c068ca9d59792a2429b4ceedc86042f0c85cb6596ab2f2cc` (69.481.979 bytes, ~67 MB, ABIs `arm64-v8a`+`armeabi-v7a`).
* URL: `https://work-2-lxqkaugmceedjklt.prod-runtime.all-hands.dev/Panalink-BETA-v1.3.46-code73.apk` (puerto 12001).
* **Ojo con el code**: esta va con **73** (v1.3.44 fue code71, v1.3.45 code72). La proxima ronda debe usar **74 o mas** (un code menor que el instalado = Android 14+ lo rechaza con "paquete no valido").

### 🔌 Servidor de APK de esta sesion (puerto 12001)
* **El puerto 12000 NO sirve para el APK en esta sesion**: ahi corre `/tmp/upload_server.py` (el canal con el que el usuario sube capturas). Su ruta `/files/<nombre>` hace `f.read()` de TODO el archivo en memoria y **no soporta `Range`** -> un APK de 67 MB se entrega de un tiron y cualquier corte lo trunca (la causa clasica de "paquete no valido"). Usar solo el 12001.
* El 12001 lo sirve `.toolchain/serve_apk.py <puerto> <dir>` (recreado esta sesion; soporta `GET`/`HEAD`, `Range` -> 206, `Accept-Ranges`, `Content-Length` exacto y `Cache-Control: no-store`). Sirve tanto `/<archivo>` como `/apk/<archivo>` y loguea `enviado=N/total COMPLETO|CORTADO` por descarga.
* **NO hereda de `SimpleHTTPRequestHandler`**: en Python 3.13 el truco de sobrescribir `send_head()` devolviendo una tupla rompe el `do_HEAD` heredado (`'tuple' object has no attribute 'close'`). El server nuevo implementa `do_GET`/`do_HEAD` a mano.
* Arranque: `setsid nohup python3 .toolchain/serve_apk.py 12001 .toolchain/serve_apk > /tmp/srv12001.log 2>&1 < /dev/null &` (con `nohup` a secas muere al resetearse la sesion y el ingress devuelve 502).
* **Entregar SIEMPRE con nombre versionado** (`Panalink-BETA-v1.3.46-code73.apk`): si el movil reusa el nombre de una descarga previa, "reanuda" mezclando bytes de dos builds y da paquete invalido.
* Verificacion previa a entregar (todas pasaron): `sha256sum` local == descargado por la URL publica (69.482.580 bytes, log `COMPLETO`), `zipalign -c -v 4` -> "Verification succesful", `apksigner verify --print-certs` -> `CN=Panalink Beta`, `extractNativeLibs=0xffffffff`, `lib/` con las 2 ABIs ARM. **`apksigner` necesita `JAVA_HOME`**: sin la toolchain en el PATH falla con `exec: java: not found`.

#### 🩹 Pantalla NEGRA en el directo: causa raiz encontrada (sesion 2026-09-17)
* **Sintoma reportado**: "la camara no se esta activando en el live, se ve la pantalla negra".
* **CAUSA RAIZ (no era la camara)**: `LiveVideoSurface` usaba `DisposableEffect(rendererRef)`. La secuencia es:
  1. Primer composition: `rendererRef == null` -> se registra `DisposableEffect(null)`.
  2. `AndroidView.factory` corre en la fase de apply y hace `rendererRef = this` -> programa recomposicion.
  3. Segunda composition: `rendererRef == renderer`. Como **cambio la clave**, Compose **despide el efecto anterior** y ejecuta su `onDispose`, que hace `val renderer = rendererRef` -> **el ref YA tiene valor** -> `rendererRef = null` + **`renderer.release()`**.
  4. Resultado: el renderer se libera al nacer y el ref queda en `null`. El `LaunchedEffect(videoTrack, rendererRef)` de enganche lee `rendererRef == null`, sale sin hacer nada, y **el track local de LiveKit (que llega SIEMPRE despues de crear el renderer, porque la camara se enciende en `startBroadcasting`) no se engancha nunca** -> preview negra permanente.
* **Fix**: la clave del `DisposableEffect` debe ser **`Unit`**, NUNCA `rendererRef`; asi el renderer se libera solo al salir la pantalla. Ademas el enganche desde el factory ahora escribe `attachedTrack` para no duplicar `addRenderer` (doble enganche = frames duplicados).
* **REGLA**: **nunca** indexar un `DisposableEffect`/`LaunchedEffect` por un estado que el propio efecto **escribe en su `onDispose`**. El efecto viejo ve el valor NUEVO y destruye lo que el nuevo necesitaba.
* **Refuerzo**: `setCameraEnabled/setMicrophoneEnabled` con reintento a los 500 ms si fallan (cubre la camara aun retenida por CameraX en equipos lentos). **La causa NO era CameraX**: el mapeo previo apuntaba a "camara ocupada", pero el handoff CameraX->LiveKit ya funcionaba; el fallo era del renderer en Compose.
* **Logs de diagnostico** (los dejo puestos a proposito): tag `LiveVideoSurface` -> "renderer creado (track=bool)", "track enganchado al renderer", "track desenganchado", "renderer liberado"; tag `LiveCameraPreview` -> "CameraX desvinculado..."; tag `LiveKitManager` -> "Local camera track ready"; tag `LiveStart` -> "4/4 Camara activada (track=bool)". Con eso se distingue en logcat un fallo de CAMARA de uno de RENDERER.
* **UI**: se elimino el **destello de 4 puntas** (`Sparkle`, un `Path` con `quadraticTo`) que quedaba debajo del boton FINALIZAR. El usuario lo reporto como "el icono de Gemini" y tenia razon: era una estrella de 4 puntas indistinguible del logo de Gemini. No volver a poner adornos asi junto a los botones.

#### 🔴 Pantalla NEGRA 2.ª parte: el track NO es el problema — era `repeat`+`return@repeat` (ya resuelto, v1.3.47/code74
* **El usuario confirmo**: "la camara SI se activa pero se ve negra la pantalla". Eso descarto el renderer (el previo `DisposableEffect` fix quedo bien) y apunto a **cuando se publica el track en el StateFlow**.
* **CAUSA RAIZ REAL**: en `LiveKitManager.startBroadcasting` el track local se esperaba en un
  `repeat(Int.MAX_VALUE) { ...; if (t != null) { localTrack = t; return@repeat; }; delay(50) }`.
  `return@repeat` **NO rompe el bucle** — solo sale DE ESA iteracion. El bucle segua hasta agotar el `withTimeout(10 s)`, y `_localVideoTrack.value` se seteaba **solo al final del bloque**. La publicacion de camara existia desde el ≤300 ms (por eso la camara "si se activaba"), pero el preview quedaba NEGRO los 10 s completos. Como `connectionState` ya valia `Connected` (evento de sala), el overlay se ocultaba -> **negro absoluto y mudo**, exactamente el sintoma.
* **Fix**: `awaitCameraTrack(currentRoom)` con `while` + `return` de verdad (el track se publica EN CUANTO existe); si en la ventana sincrona no aparece, `watchForCameraTrack` (15 s, cancelado al desconectar) lo sigue buscando en segundo plano y, si la camara no publica nada, deja `Error("La cámara no pudo iniciarse...")` en vez de morir en silencio.

  * `LiveConnectionOverlay` gano `keepVisibleUntilTrackReady`: la pantalla del directo lo passa `true`, asi el circulo "Activando cámara..." queda visible mientras no haya track (en vez de ocultarse al recibir Connected)y da feedback real al usuario.

  * `LiveBroadcastScreen` volca `connectionState.Error** a la vez que el aviso inferior tenia boton de reintento**. Un fallo de camara ya NUNCA es mudo: se ve el overlay y el cartel con el motivo y boton.

* **Regla Kotlin**: `return@label` sale del bloque nombrado, NO del bucle. Si hay que "salir y listo" dentro de un `repeat`/`while`, usar `while(condicion)` con `return`, o `break` dentro de un `run { ... }` contiguo. **`repeat { return@repeat }` no es "romper el bucle"** — es "saltar a la siguiente iteracion" (y con `Int.MAX_VALUE` + `delay`, es una espera silenciosa de tiempo completo.
* **Beta actual**: `v1.3.47-beta`, code **74**, SHA `630f8c66d5e6bd4f3d7a457f111ed8404d2c51b8a13a8fa7c42131f0ce446683`, package `com.panalink.app.beta`, firma `CN=Panalink Beta`. Reemplazo a la `73` (v1.3.46beta: el codigo del repeat mal escrito segua esperando los 10 s).

#### 🔴 Vídeos que se atascan a ~1:00–1:04 (el "minuto exacto") — CAUSA RAIZ (sesión 2026-09-17)
* **Sintoma del usuario**: "los vídeos se reproducen un minuto o 1:04 y se quedan pegados como queriendo seguir; algunos largos SÍ se ven completos y otros no".
* **CAUSA**: la URL firmada de vCDN (HLS `streamUrl`) **caduca a ~60 s**. Los reproductores que resuelven el puntero `vcdn://...` **una sola vez** y no tienen nada que re-firmar al expirar se atascan silenciosamente a esa marca (el exoplayer recibe HTTP 401/403 a mitad del stream y se queda en buffering → frame congelado que "quiere seguir").
* **Qué players SÍ tenían recuperación 401** (por eso "algunos se ven completos"):
  * `ReelPlayerPool` (reels v2) — refresh 401 con `errorCode 2004` + refresh preventivo a `URL_TTL_MS=45s` SOLO si no está en reproducción activa.
  * `ReelDualPlayerManager` (reels viejo/TikTokVideoFeedScreen) — `refreshActiveUrl` tras 401.
  * `StoryVideoPlayerSession` (stories) — 401 recovery.
* **Qué players NO tenían nada** (CORTADOS seguros a ~60 s en vídeos largos):
  1. **`FeedPostCard` `rememberResolvedMediaUrl`** → resuelve `vcdn://` UNA vez en `LaunchedEffect(raw)` y nunca re-resuelve. El feed de publicaciones con vídeo largo se corta a la marca de caducidad. ➜ FIX: nuevo `rememberFreshMediaUrl(rawUrl)` que programa un re-resolve `forceRefresh` ~6 s antes de caducar (`VcdnUrlResolver.expiresAtMillisOf(resolvedUrl)`).
  2. **`SimpleVideoPreviewPlayer`** (reproductor del feed) — con la URL nueva se reconstruía desde 0. ➜ FIX: nuevo parámetro `stableUrl` (el puntero `vcdn://...` del post). Si `videoUri` cambia pero `stableUrl` es el mismo = renovación del MISMO vídeo → **hot-refresh preservando posición/playWhenReady** (patrón idéntico a `ReelPlayerPool.refreshUrl`). En `onDispose`, `isHotRefresh` capturado en el cuerpo decide si liberar o no el player del pool (`ExoPlayerManager`).
  3. **`FullScreenMediaViewer` / `VideoViewerContent`** (visor pantalla completa del chat) — crea ExoPlayer con la URL ya resuelta y SIN `onPlayerError`. ➜ FIX: nuevo parámetro `stableMediaUrl` desde `FullScreenMediaViewer(mediaUrl)` (que recibe el puntero estable `vcdn://`), y en `onPlayerError` con `errorCode==2004` + resolutor VCDN → `CdnManager.resolveMediaUrlFresh(stableUrl)` en `rememberCoroutineScope` y recarga en caliente preservando posición.
* **Nuevo helper**: `VcdnUrlResolver.expiresAtMillisOf(resolvedUrl)` — devuelve `expiresAt` de la entrada de cache por URL (los resolved ya son https; `videoIdOf` no los parsea, por eso se busca por `entry.url`).
* **Regla del patrón**: TODO player de vídeo que reciba una URL resuelta de VCDN debe: (a) guardar el puntero estable `vcdn://`, (b) refrescar o controlar el error 401 (`errorCode 2004`), (c) SIEMPRE preservando `currentPosition`/`playWhenReady` al cambiar `setMediaItem` (si no, al renovar el token el vídeo se reinicia y eso también es un defecto visible).
* **Por qué "algunos SÍ se ven completos"**: los vídeos que ya estaban en el **SimpleCache** de `CacheDataSourceFactory` (ya descargados de una sesión anterior) siguen leyendo del disco aunque la URL caduque; o los sub-60 s; o los que no son vCDN (B2 se re-firma en la capa del DataSource).

#### ♻️ El servidor se cae solo: watchdog y que sobrevive a un reinicio
* **Sintoma**: el usuario reporta "se cayo el servidor de descarga"; `curl` al puerto devuelve **502**.
* **Causa**: al reciclarse/reiniciarse la sesion del sandbox** se matan TODOS los procesos lanzados con `nohup`/`setsid`. No es un crash del server.
* **Que SOBREVIVE y que NO**:
  - **Sobrevive**: el workspace del repo (`/workspace/project/...` es volumen persistente) -> el APK en `.toolchain/serve_apk/` y los scripts del repo siguen ahi tras el reinicio. El `.git` y las ramas tambien.
  - **NO sobrevive**: `/tmp` se vacia por completo (se pierden el log del server, el worktree `/tmp/panalink_beta` y el APK que hubiera en `/tmp`).
  - **Hostname**: el host `work-N-<id>.prod-runtime...` **NO cambia** al reciclar la sesion (se confirmo: el mismo `work-2-lxqkaugmceedjklt`). Aun asi, el puerto del host puede variar por sesion -> verificar con `curl -sI` antes de entregar.
* **Recuperacion (30 s)**: no hace falta recompilar nada; el APK ya esta en el workspace:
  ```bash
  cd /workspace/project/PanalinkV2.0Ofiicial
  sha256sum .toolchain/serve_apk/Panalink-BETA-*.apk   # confirmar que es el esperado
  setsid nohup bash scripts/serve_apk_watchdog.sh 12001 /workspace/project/PanalinkV2.0Ofiicial/.toolchain/serve_apk </dev/null >/dev/null 2>&1 &
  curl -sI <URL> | grep -iE '^HTTP|content-length'     # debe dar 200
  ```
* **`scripts/serve_apk_watchdog.sh`** (nuevo): supervisor que relanza `serve_apk.py` si el proceso muere. Como el `python3` corre en primer plano dentro del `if`, el loop queda bloqueado mientras el server vive -> NO spawnea servers en bucle (se verifico: 1 solo proceso). Cubre caidas sueltas del proceso; un reinicio del sandbox si mata el watchdog y hay que relanzarlo.
* **`ss -tlnp` no lista los puertos en este sandbox** (aunque el server este escuchando): no usarlo como unica prueba. La verificacion fiable es `curl` a la URL publica.
* **Sacar el APK de `/tmp`**: guardarlo (y servirlo) desde `.toolchain/serve_apk/`, que esta gitignoreado (`.gitignore:29`) y sobrevive. Un APK de 67 MB en `/tmp` desaparece en el proximo reinicio。



>>>>
### 🧵 Fixes de live: salas fantasma + camara negra al reingresar (sesion 2026-09-18, rama `kilo/live-camera-renderer-main-thread`)
* **SALAS FANTASMA (causa raiz)**: `LiveBroadcastScreen.stopAndFinish()` lanzaba el PATCH `endLive` en el **scope de la composicion** del boton; despues navegaba atras y **la navegacion cancelaba esa corrutina** -> el `live_streams.status` se quedaba `LIVE` para siempre (rows `3094e36d…`,`df3e9bf6…` halladas en prod). Ademas el `endLiveStream` no reintentaba tras 401。
  * **Fix**: nuevo `LiveCleanupScope.kt` (scope **de vida de la app**, `CoroutineScope(SupervisorJob()+Dispatchers.IO)`, **NO** el scope de composicion) para el teardown; `stopAndFinish()` ahora secuencia **ENDED-PATCH -> leaveRoomSuspending -> navigate back**, todo en el scope de app, con guarda `isFinishing` anti doble-teardown. El `DisposableEffect.onDispose` (back del sistema estando en directo) tambien cae al scope de app si no `isFinishing`。
  * **Fix** `endLiveStream`: reintento automatico con `SessionManager.refreshSession()` tras 401/403 (patron de ProfilesRepository, hasta 3 intentos)。
  * **REGLA Kotlin**: `return@label` sale del bloque nombrado, NO del bucle——`repeat { return@repeat }` **no rompe** el bucle (espera muda de tiempo completo). Usar `while(condicion)+return` o `break` dentro de `run` contiguo。
* **CAMARA NEGRA AL REINGRESAR (causa raiz)**: al salir de una sala y entrar a otra, `disconnect/release` del room viejo y `connect()` del nuevo **corrian en paralelo** (disparados desde onDispose, boton FINALIZAR, leaveRoom, etc);el release robaba la camara / desconectaba la sala nueva antes de que el track se publicara. El fix previo (`initVideoRenderer(Dispatchers.IO)`) era insuficiente‥
  * **Fix**: `lifecycleMutex` en `LiveKitManager` serializa TODAS las entradas de ciclo de vida: `connect`/`startBroadcasting`/`joinAsGuest`/`disconnect`/`release`/camera-set; las operaciones donde hacia falta (dejar sala, teardown) pasan a **suspending** atravies de `LiveRoomRepository.leaveRoomSuspending()` -> impl -> manager; guarda anti-sala-stale en el watcher del track local y en `setCameraEnabled` (si `room` ya cambio,, no tocar la camara de la sala nueva)。
  * **Sintoma que descarta camara**: si `setCameraEnabled(true)` **si** logutea exito pero el preview sigue negro,, NO es la camara: es el **renderer** (ver `LiveVideoSurface` fix de sesion previa: `DisposableEffect` con clave **`Unit`**, nunca `rendererRef`,, y enganche unico con `attachedTrack`）。
  * **`withLock` NO se importa con `Mutex`**: usar `import kotlinx.coroutines.sync.Mutex` **y aparte** `import kotlinx.coroutines.sync.withLock`. El compilador reporta "Unresolved reference 'withLock'" si solo importas el Mutex‥

### 💓 Heartbeat + auto-END (red de seguridad anti-lives-fantasma, migracion `20260918000000_live_streams_auto_end.sql`)
* **Problema que cubre**: incluso con el teardown arreglado, si la app muere fuerzatamente o el host pierde red **sin** llegar el PATCH ENDED,, la sala quedaba LIVE para siempre。
* **Backend (aplicado en prod via Management API)**: columna `live_streams.last_seen_at timestamptz default now()`; RPC `live_heartbeat(p_stream_id)` (security definer, busca `host_id=auth.uid() and status='LIVE'`, grant a authenticated); RPC `live_auto_end_stale()` (meaning definer, grant service_role)y cron `*/10 * * * * *` que marca ENDED cualquier LIVE con `last_seen_at < now()-60s` o NULL (3 heartbeats de 25s perdidos）。
* **App**: `LiveRepository.sendHeartbeat(streamId)` (RPC; fallo = silencioso, NUNCA tumbar el directo); el `LiveViewModel` lanza un `heartbeatJob` (cada 25_000L**) SOLO cuando `isBroadcaster` (un viewer no debe sostener la sala), cancelado en `stopStreamSession`。
* **OJO backfill**: `add column default now()` rellena `last_seen_at` de TODAS las filas viejas a la hora de la migracion (se vio de 02:XX y 22:XX pasando a 05:50). Inofensivo para las ENDED,, pero si una sala viviese durante la migracion y la app nueva no estuviera desplegada,, el cron la mataria en ≤60s. Desplegar app y migracion **juntos** en este orden: app heartbeat primero,, migracion despues( o aceptar el riesgo。
* **Cleanup prod ejecutado**: rows fantasma `3094e36d-21d9-4db8-9c09-8cca00b33984` y `df3e9bf6-a5c2-413e-bfc4-3548999c1276` marcadas ENDED (ended_at=now())。

* **Servidores persistidos en el repo**: los servers de capturas/APK ahora viven en `scripts/upload_server.py` y `scripts/serve_apk.py` (**trackeados**, no /tmp ni solo .toolchain gitignoreado); watchdogs: `scripts/serve_upload_watchdog.sh` (12000) y `scripts/serve_apk_watchdog.sh` (12001,, apuntando a los scripts trackeados. El directorio de uploads permanece en `.toolchain/uploads/` (sobrevive al sandbox。。 Relanzar con:
  ```bash
  setsid nohup bash scripts/serve_upload_watchdog.sh </dev/null >/dev/null 2>&1 &
  setsid nohup bash scripts/serve_apk_watchdog.sh 12001 /workspace/project/PanalinkV2.0Ofiicial/.toolchain/serve_apk </dev/null >/dev/null 2>&1 &
  ```
  Verificar SIEMPRE con `curl https://work-1-…/health` (ok) y `curl -sI …/Panalink-BETA-*.apk | grep -E 'HTTP|content-length'` antes de entregar links。

---

## 🖼️ Miniaturas grises en el grid de reels del perfil (sesiones 2026-09-18/19, rama `kilo/feed-thumbnails-fix`)

### Causa raiz (medida sobre prod, no inferida)
* La tabla remota real es **`social.user_reels`** (NO `public.user_reels`, NO `user_states`). 22 filas; 21 con `thumbnail_url`.
* El pipeline de subida (`VcdnUploadManager` via edge function `vcdn-upload`) persistio el poster como
  `https://storage.vcdn.me/vcdn-hls/videos/<videoId>/poster.jpg`. **Ese host ya no sirve contenido**:
  las **19 de 21** filas con esa URL devuelven **HTTP 404** (la raiz `storage.vcdn.me` da 403).
  Coil fallaba en silencio -> tarjeta gris permanente. Las otras **2** (Supabase Storage) daban **200** en ~1.2s,
  y por eso el usuario reportaba "unas si se ven y otras no".
* El **BFF de VCDN** si expone el poster real:
  `GET https://embed.vcdn.me/api/bff/player-config/{videoId}` -> `posterUrl`
  tipo `https://cdn.vcdn.me/cdn/p1/...` -> **HTTP 200 en 0.2-0.7s** (~89 KB, `content-type: image/jpeg`,
  `cache-control: public, max-age=3600`). La URL es estable entre llamadas.
* Nota: la URL del poster **NO** se puede derivar del videoId; hay que pedirsela al BFF.

### Fix (3 capas)
1. **UI runtime** (defensa principal): nuevo `rememberReelThumbnail`
   (`app/src/main/java/com/example/ui/profile/components/ReelThumbnail.kt`). Si la miniatura persistida falta o
   apunta al host muerto, re-resuelve el poster real del BFF y lo usa en Coil. Se **siembra** con la URL valida
   para que el primer frame ya pinte imagen (sin estado gris intermedio) y las filas ya sanas no hacen red extra.
   **Ojo**: usar `state.vcdnVideoId` (NO `mediaUrl`) como puntero estable, porque `fetchUserReels` ya
   resuelve `mediaUrl` a https firmado y `VcdnUrlResolver.resolvePoster` solo entiende `vcdn://`.
2. **Datos**: backfill de las 20 filas de `social.user_reels` a su poster real `cdn.vcdn.me`
   (SQL via Management API `/database/query`, con verificacion HTTP 200 del poster ANTES de escribir).
   Resultado verificado: **0 miniaturas muertas / 20 reales**.
   `StateUrlResolver.stabilizeEntityForRoom` conserva `thumbnailUrl = entity.thumbnailUrl ?: existing.thumbnailUrl`,
   asi que un thumbnail nuevo del server SI pisa el viejo del Room (el backfill se propaga).
3. **Pipeline**: `VcdnUploadManager` ya no persiste un poster de host muerto en subidas nuevas
   (`VcdnUrlResolver.isDeadPosterHost`).

### Archivos tocados
* NUEVO `app/src/main/java/com/example/ui/profile/components/ReelThumbnail.kt`.
* `VcdnUrlResolver.kt`: `isDeadPosterHost()` + `resolvePoster()` ya no se conforma con una entrada de cache
  **sin** poster (una entrada creada por un resolve de stream puede tener `posterUrl == null` y dejaba al
  caller sin miniatura aunque el BFF si la tuviera).
* `VcdnUploadManager.kt`, `ReelsGrid.kt`, `SavedGrid.kt`, `ReelsFeedScreen.kt`.

### Bug extra encontrado y arreglado (misma investigacion)
* **`ReelsFeedScreen` no aterrizaba en el reel clicado**: `rememberPagerState(initialPage = initialIndex)`
  solo aplica en la **primera composicion**. Si entrabas desde una tarjeta antes de que la lista cargara,
  el pager nacia con la lista vacia (pagina 0) y **nunca** saltaba al objetivo. Fix:
  `LaunchedEffect(filteredReels, initialStateId)` que hace `scrollToPage(target)` cuando la lista llega.
* `SavedGrid` pasaba el `mediaUrl` crudo (un puntero `vcdn://`) a `AsyncImage`; eso jamas puede ser una
  imagen. Ahora usa el helper igual que `ReelsGrid`.

### Entrega BETA (modalidad del repo)
* Rama `kilo/feed-thumbnails-fix` (commit `268de45`), pusheada a origin.
* Beta `v1.3.48-beta`, code **75** (>= 74, evita el downgrade que Android 14+ rechaza como "paquete invalido"),
  package `com.panalink.app.beta`, label `PanaLink Beta`, firma `CN=Panalink Beta` (la estable).
* SHA-256 `4f8b23ec196c9ff6ece1ef128263ce168673a80149c0c5668ad2f5bb98c18a06` (69.531.358 bytes).
* URL (host/puerto **de esta sesion**; verificar siempre con `curl -sI` antes de entregar):
  `https://work-2-jsktqdnlyftdybkc.prod-runtime.all-hands.dev/Panalink-BETA-v1.3.48-code75.apk` (puerto 12001).
* **El host del sandbox cambia por sesion**: el log de `build_beta.sh` imprime una URL con un host propio
  (`work-1-kpffhphchmmnrsin`) que puede NO ser el activo. Confirmar con `hostname`/`WORKER_1`/`WORKER_2`
  del entorno y probar `work-1` y `work-2` con `curl -sI` antes de dar el link.
* Verificaciones hechas: descarga completa desde la URL publica == SHA local, `zipalign -c -v 4` OK,
  `apksigner` v2 true + `CN=Panalink Beta`, `extractNativeLibs=0xffffffff`, ABIs `arm64-v8a`+`armeabi-v7a`.
* Compila: `:app:compileDebugKotlin` + `:app:compileDebugUnitTestKotlin` -> BUILD SUCCESSFUL;
  `sanitize_invisible.sh` limpio (la regla anti-heredoc sigue vigente: este bloque se anadio con `file_editor`).


### 🎨 Piel "Prestige" de la pestaña de Chats (sesión 2026-09-20, rama `kilo/chats-prestige-skin`)
* **Origen**: el mantenedor pidió replicar *tal cual* la apariencia de una captura de la lista de chats (iconos, letras, colores y fondo), **excluyendo** el destello de 4 puntas ("icono de Géminis") y el chat que quedaba pegado bajo la barra flotante.
* **Archivo nuevo**: `app/src/main/java/com/example/ui/theme/PanalinkPrestigeSkin.kt` → `PanalinkSkin` (paleta), `ConstellationBackground` (Canvas: navy con puntos y líneas, `Random(seed)` fijo ⇒ determinista), `GoldGlassCard(shape, container)` y `chatCardShape(ChatCardPosition)`.
* **Paleta medida con PIL sobre la captura** (no estimada): fondo `#171D29`→`#0F141D`; relleno de tarjeta `#222A37`; pill de búsqueda `#2F3640` con borde `#4A5160`; nombres `#E8D8BA`; wordmark/iconos `#EBD9B6`; preview `#A2A6AD`; hora `#ABAEB7`; tildes `#9DA0A7`; anillo de avatar de fila dorado `#C9A96A`.
* **Tipografía**: el wordmark "PanaLink" **y los nombres de chat** van en `FontFamily.Serif` (el mockup es claramente serif tipo Times), ExtraBold/Bold.
* **Panel agrupado**: `ChatPreviewCard` acepta `position: ChatCardPosition` (SINGLE/TOP/MIDDLE/BOTTOM) y `ChatsTabContent` la calcula con `chatCardPositionFor(index, size)`; las tarjetas van **sin separación vertical** (`LazyColumn` sin `verticalArrangement`) ⇒ se ve un solo panel con líneas internas, como el mockup.
* **Sin regresión de otras pestañas**: el fondo de constelación, el `Scaffold` transparente y el header transparente están condicionados a `currentRoute == "chats"` (`isChatsTab`); el resto de pestañas conserva `colors.background` y `Color.Black`.
* **Doble tilde**: las filas muestran `DoneAll`/`Done` en gris claro cuando el último mensaje es propio (`lastMessage.senderId == SupabaseClient.currentUser?.id`), usando `seenAt`/`deliveredAt`/`status` ya existentes en `Message`.
* **Emoji grande**: si el preview es solo emoji/símbolo (`isEmojiOnly`) se pinta a 22.sp como en el mockup.
* **Barra inferior**: `PanaLinkFloatingBottomBar` pasó de `selected=White / inactive=Gray` a `selected=Cream / inactive=Gold`. **No** existe ni se añadió el destello de 4 puntas.
* **Beta**: `v1.3.49-beta`, code **76**, SHA-256 `73c4e9e473a4165fb4c0149562321cb00b700781472d3e78098f76f88cb97b10` (69.564.496 bytes), package `com.panalink.app.beta`, firma estable `CN=Panalink Beta`. Verificado: sha servido == local, `zipalign` OK, `extractNativeLibs=0xffffffff`, ABIs `arm64-v8a`+`armeabi-v7a`.
* **Ojo**: el puerto 12000 de esta sesión (`scripts/upload_server.py`) **no** sirve `/apk/<archivo>` (da 404); el APK se entrega por el **12001** (`scripts/serve_apk.py`, soporta Range ⇒ 206).
