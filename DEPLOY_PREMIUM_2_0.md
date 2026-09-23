# Despliegue Premium 2.0 — Plan de producción (PAQUETE LISTO, NO APLICADO)

> Estado: paquete auditado y certificado localmente (PostgreSQL 17.11 +
> `compileDebugKotlin` + `assembleDebug` + 324 unit tests). **Este documento no
> autoriza aplicar a prod.** Se requiere revisión y aprobación explícita del
> mantenedor antes de ejecutar cualquier paso en el proyecto de Supabase real.

---

## 1. Qué se despliega

Backends de la *living economy* PanaLink Premium 2.0:

- Wallet de monedas/diamantes/tickets + ledger de transacciones idempotente.
- Entitlements premium por días (Chat/Story/Live/Voice/Wall/PanaTV).
- Recompensa diaria (racha 7 días), misiones, niveles con premios, eventos y
  promociones (framework extensible).
- Notificaciones automáticas y auditoría administrativa del balance.
- Cosméticos (marcos de avatar) desbloqueables por nivel + equipar en perfil.
- UI asociada: centro Premium, tienda, wallet, galería de marcos, gates aditivos.

## 2. Orden de aplicación (IMPORTANTE — el orden es vinculante)

Las migraciones dependen entre sí (funciones referencian tablas de la anterior).
Aplicar en **N > exacto**, nunca en paralelo:

| # | Migración | Depende de | Contenido |
|---|-----------|-----------|-----------|
| 1 | `20260921000000_premium_2_0_core.sql` | — | wallets, entitlements, premios, catálogo, recompensa diaria, misiones, niveles, triggers |
| 2 | `20260921010000_premium_notif_triggers.sql` | 1 | triggers de notificación (recompensa/evento) |
| 3 | `20260921020000_premium_audit_log.sql` | 1 | la tabla y RPC de auditoría |
| 4 | `20260921030000_premium_concurrency_hardening.sql` | 1 | advisory locks + rate limits + idempotencia por request_id |
| 5 | `20260921040000_premium_levels_wallet.sql` | 1 | niveles, wallet_history, recompensas por nivel |
| 6 | `20260921040001_premium_level_up_fix.sql` | 5 | fix de nivel (evento/trigg) |
| 7 | `20260921050000_premium_wall_panatv_products.sql` | 1,3 | productos Wall Gold / PanaTV Gold |
| 8 | `20260921060000_premium_cosmetics.sql` | 5 | marcos de avatar + equip + triggers |
| 9 | `20260921060001_premium_reward_kind_fix.sql` | 1,8 | fix de kind de recompensa |
| 10 | `20260921070000_premium_cosmetics_equipped.sql` | 8 | my_cosmetics devuelve equipped |
| 11 | `20260921080000_premium_concurrency_fixes.sql` | **NUEVO (Fase 5)** | advisory lock en claim_daily_reward / mission_claim_all / mission_progress + des-equipar 'none' |

> La migración 11 (`20260921080000`) es un **hallazgo de la auditoría de Fase 5**:
> corrige una race condition en la recompensa diaria y misiones (doble pago en
> claims simultáneos) y habilita des-equipar marcos. **Debe incluirse sí o sí.**

## 3. Checklist de verificación ANTES de aplicar

- [ ] Branch feature `kilo/premium-2.0` aprobada por el mantenedor (review).
- [ ] Copia de seguridad (PITR/SQL dump) del proyecto Supabase.
- [ ] Los 11 archivos presentes en `supabase/migrations/`.
- [ ] No hay migraciones *posteriores* que pisen estas (conflictos de catálogo).
- [ ] Red local / acceso Management API con rol suficiente.

## 4. Checklist de verificación DESPUÉS de aplicar

### Backend (SQL / API)
- [ ] `wallet_balance_full()` devuelve JSON sin error para un usuario de prueba.
- [ ] `premium_catalog()` lista productos Wall Gold / PanaTV Gold.
- [ ] `premium_buy('chat_gold_3d', 'test-<uid>-1')` debita una vez y crea entitlement.
- [ ] `premium_buy` repetido con el MISMO request_id → `duplicate_request` (sin debitar).
- [ ] `premium_buy` con saldo insuficiente → `insufficient_balance`, sin cambio de saldo.
- [ ] `premium_my_entitlements()` lista el entitlement activo con `days_left`/`expires_at`.
- [ ] `premium_expire_entitlements()` (cron) marca expirados tras la fecha.
- [ ] `claim_daily_reward()` da exactamente 1 recompensa por día (doble claim → `already_claimed_today`).
- [ ] `diamonds_exchange()` convierte con rate limit (5/min).
- [ ] `mission_progress`/`mission_claim_all` incrementa y cobra sin duplicar.
- [ ] `my_cosmetics()`/`equip_cosmetic('crown')`/`equip_cosmetic('none')` (des-equipar) OK.
- [ ] RLS: `user_wallets` sin policies (solo RPC definer acceden), `anon` no ejecuta funciones definer.
- [ ] Triggers de notificación disparan (si el flujo de FCM está habilitado).

### App (Android)
- [ ] `compileDebugKotlin` + `assembleDebug` OK.
- [ ] 324 unit tests (incl. `EntitlementModelTest`) en verde.
- [ ] Centro Premium muestra saldo, catálogo y compra al usuario.
- [ ] Login/logout: `PremiumManager.reset()` (saldo/entitlements se limpian al cambiar usuario).
- [ ] Expiración automática: si una feature vence con la app abierta, el gate se inactiva (timer).; Cero regresiones en funciones gratuitas (historia/reel/live/voice/PanaTV abren siempre).

## 5. Hallazgos documentados (Fase 5) — NO bloqueantes

1. **Triggers definer con ACL public default** (`has_default_acl=t`): hardening
   opcional (revocar a `public` tras instalar), menor.
2. **`user_cosmetics` / `wallet_transactions` fuera de Realtime**: el equipado
   viaja por `profiles.pendant_code` → `public_profiles` (correcto); no se
   observan en vivo otras tablas. Rediseñar Realtime si se necesita sincronizar
   marcos/estados entre dispositives.
3. **`equip_cosmetic` no verifica fila `profiles` existente**: si el perfil es
   recién creado (UPDATE afecta 0 filas) devuelve `ok` sin persistir. En la app
   el perfil se crea al signup, riesgo bajo; se puede endurecer con
   `IF NOT FOUND THEN ...`.
4. **Stacking (comprar el mismo producto dos veces) crea filas separadas** en
   `user_entitlements` en lugar de extender la misma. No es un bug (el server
   calcula `days_left` sumando), pero es una decisión de diseño a revisar si se
   prefiere "extender el entitlement actual".

## 6. Cómo revertir (rollback)

- Las 11 migraciones son aditivas (CREATE) — **no destructivas**. Para revertir
  se debe mayormente DROP de los objetos premium (funciones/tablas/triggers) en
  orden inverso, lo cual **no** toca datos de la app core (mensajes, perfiles,
  media). El orden de DROP debe ser inverso al de aplicación.
- `user_wallets`/`user_entitlements` se pueden dejar como tablas inertes si se
  quiere conservar saldos ante re-despliegue.

---

*Documento generado por un agente de IA (OpenHands) en el contexto del repositorio PanaLink, sin cambios a producción.*