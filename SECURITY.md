# Seguridad

## Reportar una vulnerabilidad

Panalink toma la seguridad en serio. Si descubrís una falla de seguridad **NO la reportes en un issue público** (un atacante podría aprovecharla antes del fix).

Reportala directamente al mantenedor:

- **Email/contacto**: por el canal privado del mantenedor (GitHub → perfil → contacto.
- **Asunto**: `[PANALINK-SECURITY] <descripción corta>`
- **Incluí**: versión de la app, pasos para reproducir, impacto potencial y (si lo tenés) una propuesta de mitigación. No incluyas datos de usuarios reales.



## Qué ocurre después

1. El mantenedor te acusa recibo en ≤48 h.
2. Se evalúa el impacto y se trabaja en un fix antes de divulgar.
3. Cuando el fix está publicado, se te reconoce ( si querés.

 

## Política de divulgación

- Los issues de seguridad se mantienen **privados** hasta que exista fix.
- No se paga recompensa por ahora; se agradece con crédito público si lo deseás.



## Buenas prácticas para contribuidores

- **Nunca** incluyas secretos en código, tests ni ejemplos — ni siquiera "de prueba". Usá valores mock claramente falsos (`sk_live_REPLACE_ME`) y variables de entorno o BuildConfig para lo real.
- Si un secreto real se pushea por error: asumí que está comprometido y avisá al mantenedor de inmediato para rotarlo.
. El CI tiene un [escáner de secretos](.github/workflows/security-scan.yml) que bloquea PRs con patrones sospechosos; si te bloquee, eliminá el valor y re-pushea.