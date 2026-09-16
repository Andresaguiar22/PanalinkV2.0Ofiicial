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
