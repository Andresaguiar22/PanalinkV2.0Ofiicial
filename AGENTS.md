# AGENTS.md — PanaLink V2.0 (Android, Kotlin + Compose, Supabase)

Este documento es la guía de trabajo para cualquier agente o persona que toque este repo. Léelo completo antes de hacer nada.


## 1. Qué es este repo

* App Android nativa (**Kotlin + Jetpack Compose**) de mensajería tipo WhatsApp 2.0.
* Backend: Supabase (Postgres + Realtime).
* Distribución de builds/actualizaciones: **OTA propio vía GitHub Releases** (NO Play Store).
* CI/CD: GitHub Actions (workflow único `.github/workflows/panalink-pipeline.yml`).
* Repo de distribución OTA público: `Andresaguiar22/panalink-ota` (rama `main`).


## 2. Reglas de oro

1. **`manifest.json` de la raíz es la fuente de verdad de versión OTA.** El CI/gradle lo leen para `versionCode` y `versionName`. No inventar versiones en otro lado.

2. **El APK canónico es el que produce el CI** (artifact `app-release` del workflow). Un APK manual local NO debe sustituirlo.


3. **Nunca commitear credenciales**: `google-services.json`, keystore, `secrets.properties` reales, PATs. Todo vive en GitHub Actions secrets o en `secrets.defaults.properties` (placeholders).

.


4. **SIEMPRE incrementar `versionCode` de 1 en 1** antes de publicar OTA. El `versionName` es la tag (`vX.Y.Z`), sin la `v` en el nombre OTA interno.



5. **`release-key.jks` de la raíz es EL keystore de firma** (mismo con el que se firmó la actual v1.3.21). Cualquier APK publicado DEBE estar firmado con él ( si no, la app lo rechaza al instalar sobre la versión existente (firma mismatch.


6. **La app descubre la OTA desde el `manifest.json` de `main` en `Andresaguiar22/panalink-ota`** → es la única URL que se debe actualizar al publicar.


## 3. Entorno de build (sandbox)

* Toolchain persistente en `/workspace/project/toolchain`; instalarlo una sola vez con:
  ```bash
  bash scripts/setup_toolchain.sh
  ```
* Antes de compilar, exportar el entorno:
  ```bash
  source scripts/toolchain_env.sh
  ```
  (exporta `JAVA_HOME`, `ANDROID_HOME`, `GRADLE_USER_HOME`).
* `gradle/wrapper/gradle-wrapper.jar`: si falta o está corrupto, restaurarlo desde `https://raw.githubusercontent.com/gradle/gradle/v9.3.1/gradle/wrapper/gradle-wrapper.jar`.
* `app/google-services.json` NO está committeado (gitignoreado); la CI inyecta desde el secret `GOOGLE_SERVICES_JSON`.
* No hay dependencias externas nuevas para el OTA (el script de publicación usa solo stdlib de Python 3.


### Compilar release (firmado)
```bash
source scripts/toolchain_env.sh
./gradlew :app:assembleRelease
```
* Nota: el `app/build.gradle.kts` lee `../manifest.json` para `versionCode`/`versionName` por defecto (si querés override: env `VERSION_CODE`/`VERSION_NAME`).
* Release firmado requiere: `KEYSTORE_FILE` (o `release-key.jks` local), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. En debug no hace falta.


## 4. Secrets de CI (repo app)

| Secret | Uso |
|---|---|
| `GOOGLE_SERVICES_JSON` | Contenido de `app/google-services.json` (Firebase; inyectado en build). |
| `KEYSTORE_BASE64` | `release-key.jks` codificado en base64 (se escribe como `app/release-key.jks` en el CI). |
| `KEYSTORE_PASSWORD` | Password del keystore. |
| `KEY_ALIAS` | Alias de la key de firma. |
| `KEY_PASSWORD` | Password de la key (si vacío, usa la del keystore). |
| `OTA_TOKEN` | PAT con scope `repo` del user `Andresaguiar22` — usado por el step de publicación OTA. **NO usar nombres `GITHUB_*`** (GitHub los rechaza). |

> ⚠️ Si un secret falta en el CI, el workflow falla visiblemente (build con GOOGLE_SERVICES_JSON/KEYSTORE falla; OTA con OTA_TOKEN ausente solo avisa y salta`.


## 5. Pipeline CI/CD (`.github/workflows/panalink-pipeline.yml`)

Triggers:
* **push a `main`/`master`/`develop`** → build de CI (sin publicar OTA.
* **push de tag `v*`** → build + **publica OTA automáticamente** (si `OTA_TOKEN` presente).
* **`workflow_dispatch` manual** con inputs: `changelog`, `is_mandatory`, `minimum_version`, `pub_ota` (`true`/`false`, default false).

Flujo del job `build-and-deploy`:
1. Checkout + setup JDK 21 (Temurin).
2. Inyectar `GOOGLE_SERVICES_JSON`, decodificar `KEYSTORE_BASE64` → `app/release-key.jks`, escribir `app/secrets.properties` (solo para build).
3. Build `assembleRelease` (firmado. Subir artifact `app-release` (APK).
4. **Step opcional "Publish OTA Release"** (solo tag `v*` o `pub_ota=true`): si `OTA_TOKEN` falta → warning + skip; si presente → `.github/scripts/publish_ota.py` crea/actualiza el release en `panalink-ota`, sube `Panalink-v<ver>.apk` + `manifest.json`, actualiza el `manifest.json` de `main`, y con `OTA_PUBLISH=1` lo pone **live**.
5. Clean up: borra `app/release-key.jks`, `release-key.jks`, `app/secrets.properties`.



## 6. Cómo publicar la siguiente OTA (runbook)

### Paso 0 — Editar el manifest correctamente (LA CLAVE)

El `manifest.json` de la raíz debe quedar así antes de taggear:
```json
{
  "versionCode": 49,
  "versionName": "v1.3.22",
  "downloadUrl": "https://github.com/Andresaguiar22/panalink-ota/releases/download/v1.3.22/Panalink-v1.3.22.apk",
  "apkUrl": "https://github.com/Andresaguiar22/panalink-ota/releases/download/v1.3.22/Panalink-v1.3.22.apk",
  "sha256": "<64 hex — se llena automáticamente al publicar>",
  "minimumSupportedVersionCode": 48,
  "mandatory": false,
  "changelog": ["Primer bullet", "Segundo bullet"]
}
```
* `versionCode` = anterior +1 (ahora 48 → próximo **49**).
* `versionName` = la tag exacta con `v` (próximo **v1.3.22**; siguiente v1.3.23…).
* `sha256` **se puede dejar vacío/placeholder** — el script `publish_ota.py` lo rellena con el hash real del APK (NO hay que calcularlo a mano).
* `downloadUrl`/`apkUrl` = la URL del release del repo OTA con la tag nueva (formato exacto del ejemplo).
* `minimumSupportedVersionCode` = el `versionCode` **anterior** (48). Si es mayor que el `versionCode` actual, la app exigiría otra actualización previa → riesgo de bucle.
* `mandatory` = `false` casi siempre (true solo si es obligatorio).
* `changelog` = array de strings (cada uno un bullet que se mostrará en la app.

> ⚠️ **CRÍTICO para que la app reconozca e instale sin errores**:
> * La app compara `versionCode` del manifest remoto vs su `versionCode` interno: el remoto **debe ser mayor** que el instalado.

> * `sha256` DEBE ser el del APK exacto servido por la URL (el script lo hace solo; jamás copiar un hash de otra build).
> * La firma del APK servido debe ser la misma que la versión instalada (mismo `release-key.jks`).

### Paso 1 — Commitear el manifest + código
```bash
git add manifest.json app/applet/manifest.json
git commit -m "chore(ota): bump to v1.3.22 (versionCode 49)"
git push origin develop        # o la branch de trabajo
```

### Paso 2 — Taggear y publicar (automático)
```bash
git tag v1.3.22
git push origin v1.3.22
```
El CI compila, firma y publica solo la OTA en `panalink-ota` (release + assets + manifest de main -> live).

### Paso  ̈3 — Verificar
1. Release en `https://github.com/Andresaguiar22/panalink-ota/releases/tag/v1.3.22` → **draft: false**, asset `Panalink-v1.3.22.apk` presente.
2. `curl -s https://raw.githubusercontent.com/Andresaguiar22/panalink-ota/main/manifest.json` → `versionCode` 49, `versionName` v1.3.22, `sha256` presente, `downloadUrl` apuntando al tag nuevo.
3. Instalar el APK sobre la versión anterior (mismo signature → no debe pedir desinstalar).

### Alternativa manual (sin tag)
Actions → `PanaLink V2.0 Unified Pipeline` → **Run workflow** → branch + inputs:
* `pub_ota: true` para publicar.
* `changelog` (texto de bullets);`is_mandatory` (`true|false`);`minimum_version` (versionCode mínimo. Si `map` esta ausente, el workflow usa los valores ya commiteados del manifest.


## 7. Script de publicación OTA (`.github/scripts/publish_ota.py`)

* Env vars que lee: `GITHUB_TOKEN` (**obligatoria**), `OTA_REPO` (default `Andresaguiar22/panalink-ota`), `OTA_TAG` (si se omite, deriva de `versionName` del manifest con prefijo `v`), `OTA_PUBLISH` (`1`/`true`/`yes`/`on` → publica live; otro valor o ausente → deja **draft**).
* Qué hace:
  1. Lee `app/build/outputs/apk/release/app-release.apk` y computa su `sha256`.
  2. Lee el `manifest.json` raíz (fuente de versión.

  3. Busca release existente con la misma tag (idempotente: borra assets stale y re-subir — útil para re-runs.
.
  4. Sube el APK como `Panalink-v<ver>.apk` al release (draft primero.
.
  5. Actualiza el `manifest.json` de `main` en el repo OTA (mismo contenido del raíz, con `sha256`, `downloadUrl`, `apkUrl` reales).
  6. Adjunta el `manifest.json` como asset del release.

  7. Si `OTA_PUBLISH=1` → PATCH `draft: false` (release queda **visible/público**).


## 8. Checklist rápido pos-publicación

- [ ] `versionCode` mayor que el instalado por el user (y `minimumSupportedVersionCode` = el anterior).
- [ ] `sha256` del manifest == `sha256sum` del APK servido.

- [ ] Release **draft: false** con ambos assets (APK + manifest.json).
- [ ] `downloadUrl`/`apkUrl` apuntan al tag correcto del repo `Andresaguiar22/panalink-ota`.
- [ ] El APK instaló sobre la anterior **sin perder datos ni pedir desinstalación** (misma firma.
.
- [ ] El `manifest.json` de `main` es idéntico al del release (y al que la app descargará).


## 9. Efemérides OTA (histórico

* v1.3.20 — versionCode 47 (2026-09-03).
* v1.3.21 — versionCode 48 (2026-09-03; publicada por agente OpenHands; sha256 `8c4c3472...`). Próximo: versionCode 49 / v1.3.22.