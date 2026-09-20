# Pull Request

Gracias por contribuir. Antes de que el mantenedor revise, verificá que cumplís esto:

## Checklist del contribuidor

- [ ] Leí el [CONTRIBUTING.md](../CONTRIBUTING.md)** completo (si no existe en la rama: es del `main`).
- [ ] Mi rama sale de un **fork** (o de una rama feature del repo, si tengo acceso).
- [ ] El build compila: `./gradlew --no-daemon :app:compileDebugKotlin` → `BUILD SUCCESSFUL`.
- [ ] Corrí `bash scripts/sanitize_invisible.sh` si toqué código Kotlin.

- [ ] **No** incluyo secretos (ni `google-services.json`, ni keystores, ni claves de API, ni tokens). Ninguno, punto.

- [ ] No incluyo datos personales de usuarios reales en código, commits ni ejemplos.



## Descripción

_explicá qué cambia este PR y por qué (máximo 10 líneas). Relacioná el issue si existe: `Fixes #123`.


_Esta acción fue realizada por un agente de IA (OpenHands) en nombre del mantenededor._