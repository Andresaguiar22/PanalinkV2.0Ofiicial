# Contribuyendo a Panalink

¡Gracias por tu interés en ayudar! Este documento explica **cómo colaborar sin comprometer la seguridad ni la propiedad del proyecto**.

Panalink es una aplicación **privada** (todos los derechos reservados). El acceso al repositorio es un privilegio que se concede caso a caso. Leelo completo antes de tocar nada.

---

## 1. Modelo de colaboración: fork + Pull Request

Usamos el flujo estándar de GitHub. **No** damos acceso de escritura directo a `main` a colaboradores externos (ni a los internos sin revisión).

```text
1. [Fork](https://docs.github.com/es/get-started/quickstart/fork-a-repo) el repositorio a TU cuenta.
2. Crea una rama descriptiva: `fix/nombre-corto`, `feature/nombre-corto`.
3. Haz tus cambios y **prueba que compila** (ver "Compilación" más abajo).
4. Abre un **Pull Request** contra `main` describiendo qué cambias y por qué.
5. El mantenedor revisa, pide cambios si hace falta, e integra cuando esté listo.
  - Los PRs se revisan **línea por línea**. No es desconfianza personal: es lo que hace que el proyecto siga siendo seguro y de calidad.

### Reglas de la rama `main` (protegida)
- **Nunca** se pushea directo a `main`. Solo vía Pull Request revisado.
- Están prohibidos: `force push` y `delete branch` sobre ramas protegidas.
- Los PRs quedan **stale** (obsoletos) si `main` avanza: re-sincroniza tu rama antes de que se integre.



## 2. Lo que SÍ y lo que NO se toca

### ✅ Bienvenido (y valorado)
- Bugs de UI, crashes, problemas de rendimiento y accesibilidad.

- Tests nuevos o corregidos. Refactors pequeños con cobertura explicada.

- Documentación interna del código cuando sea necesario.



### ⛔ NO tocar/NO pedir (protección de la propiedad)
- **Keystores y firmas**: los keystores de producción y beta son del dueño. Solo se inyectan como secretos de CI o del entorno local.

- **Secretos**: `google-services.json`, claves de Supabase/Giphy/vCDN, tokens de servicio. **Jamás** deben aparecer en código, commits o PRs (ni siquiera "por un momento").
- **Infraestructura**: schema de Supabase, edge functions, buckets de storage, configuración de vCDN y cron jobs se gestionan aparte del repo público de colaboradores (se comparten solo con el mantenedor.

- **Roadmap y decisiones de producto**: se discuten con el mantenedor **antes** de escribir código, no después (evita trabajos que no se integran.



## 3. Seguridad: regla de oro

1. **Nunca** commitees un secreto. Si por error pusheas uno, **avisa al mantenedor inmediatamente** y rota la clave en el servicio correspondiente: asumir que queda comprometida.Una vez subido a GitHub, da por hecho que alguien lo copió.
2. **Nunca** incluyas datos personales de usuarios reales en issues, PRs o ejemplos de código. Anonimiza todo.

3. **Código de terceros**: si copias código de otra parte, cítalo y respeta su licencia. No traigas dependencias de repositorios no oficiales ni ejecutes scripts de fuentes no verificadas.



## 4. Cesión de derechos (CLA implícito

Al abrir un PR a este repositorio, **cedes al mantenedor** los derechos de usar, modificar y distribuir tu contribución como parte del proyecto (bajo los términos que el mantenedor decida, hoy "todos los derechos reservados". Esto es necesario para poder integrar tu trabajo sin conflictos legales. Si no estás de acuerdo, no abras el PR.


## 5. Compilación y verificación (obligatorio antes de abrir el PR

Panalink usa **Kotlin + Jetpack Compose + Gradle 9.3.1 + JDK 17**.

```bash
# 1. Preparar la toolchain (solo la primera vez)
bash scripts/setup_toolchain.sh
source scripts/toolchain_env.sh

# 2. Generar google-services.json local de prueba (NO es real; el real solo existe en CI/como secreto)
# Si scripts/setup_toolchain.sh no lo generó: crea un dummy SOLO para compilar:

#  ️ 3. Compilar
./gradlew --no-daemon :app:compileDebugKotlin
```

- El build **debe** quedar `BUILD SUCCESSFUL` antes de abrir el PR.
- Si tocas la UI: corre tambien: `bash scripts/sanitize_invisible.sh` (elimina caracteres Unicode invisibles que corrompen el código).
- **No** subas: `secrets.defaults.properties`, `app/google-services.json`, keystores, `.toolchain/`, `.gradle-home/`, `dist/` (ya están en `.gitignore`).


## 6. Código de conducta

Todo contributor debe seguir el [Código de Conducta](CODE_OF_CONDUCT.md). Comportamiento respetuoso, sin ataques personales ni críticas destructivas. Las violaciones se tratan en privado y pueden conllevar la expulsión del proyecto.





## 7. Proceso de revisión

1. Un PR se revisa en máximo 7 días hábiles (suele ser antes).
2. Feedback concreto y respetuoso; pide cambios, no los fuerces sobre la rama del autor (
3. El CI (si está configurado en tu PR) debe pasar. El mantenedor puede pedir cobertura de tests para cambios de lógica.




##  ️ Resumen en una frase

> **El mantenedor es el único que puede tocar secretos, infraestructura y `main`. Los colaboradores aportan con código de calidad vía Pull Requests revisados, cediendo los derechos de su contribución al proyecto.**