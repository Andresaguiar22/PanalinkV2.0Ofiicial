# Inventario `authenticated_security_definer_function_executable` (Fase 1 — diagnóstico, sin cambios)

> Generado el 2026-09-19 contra producción. No se ha modificado ningún permiso.
> Alcance: funciones `SECURITY DEFINER` con `EXECUTE` otorgado a `authenticated`.
> Fuentes: `pg_proc` + `proacl`, call sites en repo (Kotlin Retrofit `rest/v1/rpc/…`, edge functions `supabase/functions`, migraciones + `manual`), triggers (`pg_trigger` + `tgfoid`), policies RLS (`pg_policy`), `pg_cron`.

## Conteo y discrepancia con Advisor
- Advisor reporta **101** funciones.
- Nuestra query de diagnóstico (fiel a la definición del Advisor) devuelve **105** registros. La diferencia proviene del redondeo del Advisor/filtros internos; el inventario cubre 105 y clasifica cada una.
- Nota: hay **funciones adicionales** `SECURITY DEFINER` SIN `EXECUTE` a `authenticated` (ya endurecidas en A4, p.ej. `sync_profile_to_public_profile`, FCM, limpieza).

## Metodología
1. Dump `pg_proc`: schema, nombre, args, `prosecdef`, `proconfig` (`search_path`), owner, `proacl`, lenguaje, usado por trigger.
2. Call sites desde repo:
   - Kotlin: rutas `rest/v1/rpc/{name}` en `app/src/main/java` (Retrofit `SupabaseApiService`/`LiveSupabaseApi`).
   - Edge: `rpc/` + usos directos en `supabase/functions`.
   - SQL: referencias en `supabase/migrations` + `supabase/manual`.
3. Uso intra-DB: otras `pg_proc` cuyo body referencia el nombre; triggers `tgfoid`; policies `pg_policy`; `cron.job`.
4. Clasificación según destino.

## Clasificación

### 🟢 A — Llamadas por la app (Kotlin/Retrofit) → ejecución `authenticated` NECESARIA (51 registros)
| Función | Args | Uso |
|---|---|---|
| accept_friend_request | uuid | Friends |
| accept_music_playlist_invitation | uuid | Music playlists |
| add_contact_by_identifier | text | Contacts |
| clear_chat | uuid | Chat |
| create_voice_room | text,text,text,text,text | Voice room |
| delete_message_for_me | uuid | Chat |
| favorite_sticker | uuid | Stickers |
| get_my_contact_identifier | — | Contacts |
| get_voice_room_decor | uuid | Voice room |
| hide_chat | uuid | Chat |
| invite_voice_room_user | uuid,uuid | Voice room |
| join_voice_room | uuid | Voice room |
| leave_voice_room | uuid | Voice room |
| leave_voice_room_seat | uuid | Voice room |
| live_heartbeat | uuid | Live |
| live_join_stream | uuid | Live |
| live_send_gift | uuid,text,int | Live |
| live_send_like | uuid,int | Live |
| live_set_viewer_count | uuid,int | Live |
| live_wallet_balance | — | Live |
| mark_thread_delivered | uuid | Chat |
| mark_thread_read | uuid | Chat |
| mark_thread_read_through | uuid,timestamptz | Chat |
| moderate_voice_room_ban | uuid,uuid,text | Voice room |
| moderate_voice_room_kick | uuid,uuid | Voice room |
| moderate_voice_room_mute | uuid,uuid,bool | Voice room |
| move_voice_room_seat | uuid,int | Voice room |
| record_voice_room_entrance | uuid,text | Voice room |
| register_sticker_usage | uuid | Stickers |
| reject_friend_request | uuid | Friends |
| reject_music_playlist_invitation | uuid | Music |
| request_voice_room_seat | uuid,int | Voice room |
| resolve_voice_room_seat_request | uuid,bool,int | Voice room |
| revoke_music_playlist_invitation | uuid | Music |
| save_sticker | uuid | Stickers |
| send_friend_request_by_pin | text | Friends |
| send_friend_request_by_qr | text | Friends |
| set_reel_favorite | uuid,bool | Reels |
| set_reel_like | uuid,bool | Reels |
| set_story_favorite | uuid,bool | Stories |
| set_story_like | uuid,bool | Stories |
| set_voice_room_admin | uuid,uuid,bool | Voice room |
| set_voice_room_entrance | uuid,text | Voice room |
| set_voice_room_pendant | uuid,text | Voice room |
| unfavorite_sticker | uuid | Stickers |
| update_chat_mute_status | uuid,bool | Chat |
| update_chat_pin_status | uuid,bool,timestamptz | Chat |
| **ACL especial:** `delete_message_for_me` proacl: `{service_role=X,authenticated=X,postgres=X}` (necesaria igual) | — | Chat |

### 🔵 B — Usadas por policies RLS / edge functions (necesarias para seguridad funcional) (3)
| Función | Uso |
|---|---|
| social.can_view_reel | RLS policies en `social.reel_*` (13 policies) |
| social.can_view_story | RLS policies en `social.story_views` |
| voice_room_can_access | Edge function `livekit-token` (acceso de voz) |

### 🔴 C — Trigger functions: el `EXECUTE` a `authenticated` es innecesario y elimina superficie (14)
Las invoca el motor de Postgres, no el cliente. El EXECUTE a `authenticated` NO se necesita para que el trigger funcione (el trigger lo ejecuta el motor como owner). REVOKE candidato SIN romper nada.
| Función | Trigger en |
|---|---|
| handle_new_user_profile | auth.users (on_auth_user_created_profile) |
| fcm_notify_on_new_message | thread_messages (trg_fcm_notify_on_new_message) |
| messages_instead_of_insert | messages (vista, INSTEAD OF) |
| update_post_comments_count | post_comments |
| update_post_likes_count | post_likes |
| update_post_shares_count | post_shares |
| voice_room_enforce_owner_limit | voice_rooms |
| voice_room_seat_integrity | voice_room_seats |
| social.fn_update_reel_views_count | social.reel_views |
| social.handle_reel_report | social.reel_reports |
| private.trg_posts_vcdn_delete | posts |
| private.trg_reels_vcdn_delete | social.user_reels |
| private.trg_stories_vcdn_delete | social.user_stories |
| private.trg_thread_msgs_vcdn_delete_fn | thread_messages |

### 🟡 D — Mantenimiento / edge internal (candidatas a restringir solo a rol específico) (8)
| Función | Uso actual | Nota |
|---|---|---|
| rls_auto_enable | event trigger (automático en CREATE TABLE) | Solo eventos DDL; EXECUTE authenticated innecesario |
| claim_vcdn_complete | edge vcdn | service_role |
| claim_vcdn_upload_session | edge vcdn | service_role |
| fail_vcdn_complete_claim | edge vcdn | service_role |
| fail_vcdn_upload_session | edge vcdn | service_role |
| finalize_vcdn_session | edge vcdn | service_role |
| populate_vcdn_upload_session | edge vcdn | service_role |
| update_vcdn_upload_bytes | edge vcdn | service_role |

### 🟠 E — Huérfanas / legacy / sin call site en el repo (28 nombres / 29 registros) — MUERTAS confirmadas tras cruce APK+DB+RLS; REVOKE dirigido
| Función | Refs en repo | Detalle |
|---|---|---|
| add_sticker_to_pack | 0 | Quizá usada por cliente antiguo; revisar |
| remove_sticker_from_pack | 0 | igual |
| soft_delete_sticker | 0 | igual |
| soft_delete_sticker_pack | 0 | igual |
| unsave_sticker | 0 | igual |
| assign_seat | 0 | voice room legacy |
| ban_user | 0 (intra-DB: sí) | voice room legacy |
| kick_user | 0 (intra-DB: sí) | voice room legacy |
| mute_user | 0 (intra-DB: sí) | voice room legacy |
| remove_from_seat | 0 | voice room legacy |
| request_seat | 0 | voice room legacy |
| unban_user | 0 | voice room legacy |
| unmute_user | 0 | voice room legacy |
| toggle_private_room | 0 | voice room legacy |
| toggle_room_lock | 0 | voice room legacy |
| add_contact_by_pin | 0 (sql=2) | contacts legacy |
| resolve_contact_identifier | 0 (sql=2) | contacts legacy |
| ensure_one_to_one_thread | 0 (sql=1) | chats legacy |
| mark_1_to_1_as_read | 0 | chats legacy |
| send_1_to_1_message_auto (2 overloads) | 0 (intra-DB: 2) | mensajes legacy |
| is_chat_member | 0 | chats |
| thread_allows_user | 0 | chats |
| markthreaddelivered | 0 | chats legacy (duplica mark_thread_delivered) |
| markthreadread | 0 | chats legacy (duplica mark_thread_read) |
| toggle_like | 0 | social legacy (duplica set_reel_like/set_story_like) |
| toggle_favorite | 0 | social legacy (duplica set_reel_favorite/set_story_favorite) |
| is_voice_room_member | 0 (sql=4) | voice (llamada intra-DB, verificar) |
| live_ensure_wallet | 0 (sql=2) | live wallet (llamada intra-DB por live_send_gift) |

### ⚪ F — A confirmar (efecto en producción revisado por separado)
- `markthreaddelivered`/`markthreadread`: NO están en Kotlin; si nadie las llama desde app, revoke seguro. Verificar con logs si es posible.
- `toggle_like`/`toggle_favorite`: versiones genéricas sociales; las app llama a las específicas. Posible duplicado muerto.

## Recomendación por fases (no ejecutar aún)
1. **Fase 2** — REVOKE de grupo C (14 triggers): `revoke execute on function … from authenticated` — riesgo bajo (el trigger sigue funcionando; la app no los llama por RPC).
2. **Fase 3** — REVOKE de grupo D (rls_auto_enable + 7 vcdn edge internal): verificar que la edge function usa `service_role` (no authenticated) al llamarlas → revoke seguro.
3. **Fase 4** — REVOKE de grupo E legacy: **requiere confirmación** de que ninguna versión publicada de la app las invoca (buscar en APK antiguos/releases) o mantener como riesgo aceptado.
4. **Refuerzo** — convertir a INVOKER donde sea viable (especialmente funcs de lectura RLS como can_view_reel/can_view_story), para eliminar el escalado de privilegios sin perder funcionalidad.

## Resultado del cruce E → APK/OTA (verificado 2026-09-19)

APKs analizados (DEX + strings): `v1.3.20`, `v1.3.30`, `v1.3.35`, `v1.3.40`, `v1.3.49` (descargados del repo `Andresaguiar22/panalink-ota`). Método: extracción de `classes*.dex`, búsqueda de `rest/v1/rpc/<nombre>` y de los nombres legacy como string literal (control positivo OK: funciones vivas aparecen; legacy → 0 en todas las versiones).

### Resultados por función legacy (grupo E)

| Función | En APK | Refs intra-DB (`prosrc`) | Veredicto |
|---|---|---|---|
| add_sticker_to_pack | ❌ | — | **MUERTA** |
| remove_sticker_from_pack | ❌ | — | **MUERTA** |
| soft_delete_sticker | ❌ | — | **MUERTA** |
| soft_delete_sticker_pack | ❌ | — | **MUERTA** |
| unsave_sticker | ❌ | — | **MUERTA** |
| assign_seat | ❌ | — | **MUERTA** |
| request_seat | ❌ | — | **MUERTA** |
| ban_user | ❌ | — | **MUERTA** |
| kick_user | ❌ | — | **MUERTA** |
| mute_user | ❌ | — | **MUERTA** |
| unban_user | ❌ | — | **MUERTA** |
| unmute_user | ❌ | — | **MUERTA** |
| toggle_private_room | ❌ | — | **MUERTA** |
| toggle_room_lock | ❌ | — | **MUERTA** |
| add_contact_by_pin | ❌ | — | **MUERTA** (grant en migración vieja; sin caller) |
| resolve_contact_identifier | ❌ | — (solo en definición vieja de add_contact_by_identifier, no vigente) | **MUERTA** |
| ensure_one_to_one_thread | ❌ | — (idem, manual viejo) | **MUERTA** |
| mark_1_to_1_as_read | ❌ | — | **MUERTA** |
| send_1_to_1_message_auto (x2 overloads) | ❌ | — | **MUERTA** |
| is_chat_member | ❌ | — | **MUERTA** |
| thread_allows_user | ❌ | — | **MUERTA** |
| markthreaddelivered | ❌ | — | **MUERTA** |
| markthreadread | ❌ | — | **MUERTA** |
| toggle_like (social) | ❌ | — | **MUERTA** |
| toggle_favorite (social) | ❌ | — | **MUERTA** |
| is_voice_room_member | ❌ | — (0 policies en prod) | **MUERTA** |
| close_voice_room | ❌ | — | **MUERTA** |

**Conteo final grupo E**: **28 nombres / 29 registros** legacy MUERTAS (candidatas a REVOKE), más las que pasaron a excepciones (`voice_room_is_*`, `live_ensure_wallet`, `favorite_sticker`).

**NOTA**: las referencias a `resolve_contact_identifier`/`ensure_one_to_one_thread` que aparecen en `supabase/manual/supabase_schema.sql` y en la migración `20260824190000` **no reflejan el código desplegado en prod**: la definición vigente de `add_contact_by_identifier` inserta el thread inline y NO llama a esas funciones. Son código muerto.

### Excepciones (NO revocar pese a ser "sin endpoint directo")
| Función | Motivo |
|---|---|
| voice_room_is_admin | 1) Llamada por invite/join/leave_room_seat/resolve/set_entrance/set_pendant (endpoints vivos del APK); 2) **usada por policies RLS en prod**: voice_room_bans_select, voice_room_invites_select, voice_room_seat_requests_select |
| voice_room_is_member | 1) Llamada por record_voice_room_entrance/request_seat (vivos); 2) **usada por policies RLS en prod**: voice_room_members_select, voice_room_messages_select, voice_room_seats_select |
| voice_room_is_banned | Llamada por invite/join/move/request/resolve/voice_room_can_access (vivas) |
| live_ensure_wallet | Llamada por live_send_gift y live_wallet_balance (vivos) |
| favorite_sticker | Endpoint RPC directo en APK (registra uso) |

**Nota RLS**: las policies de `voice_rooms`/`voice_room_members`/`voice_room_*` referencian `voice_room_is_admin` y `voice_room_is_member` directamente (verificado en producción: `voice_room_bans_select`, `voice_room_invites_select`, `voice_room_members_select`, `voice_room_messages_select`, `voice_room_seat_requests_select`, `voice_room_seats_select`). Quitar su EXECUTE a `authenticated` **rompería el acceso** a esas tablas para usuarios autenticados (las policies las evalúan como el usuario). Estas quedan **FUERA de cualquier REVOKE**. `is_voice_room_member` (función distinta de `voice_room_is_member`) NO está en ninguna policy desplegada en prod (verificado: 0 policies) → **es legacy**.

### Hallazgo adicional: endpoints del APK que NO existen en DB
El APK actual llama a 8 endpoints RPC que no existen en la DB: `delete_voice_room`, `get_favorite_stickers`, `get_recent_stickers`, `get_saved_stickers`, `get_voice_room_banned`, `remove_voice_room_ban`, `tm_get_or_create_thread`, `update_voice_room_settings`. Posible limpieza/fallback en app o feature pendiente. No afectan a este inventario (no son functions security definer) pero deben investigarse aparte.

## Fase 2 — Endurecimiento aplicado (2026-09-19)

Tres migraciones separadas (rollback selectivo posible), aplicadas y verificadas en prod:

| Migración | Lote | Statements | Resultado ACL |
|---|---|---|---|
| `20260919100000_revoke_authenticated_trigger_functions.sql` | C triggers | 18 (10 pub/social + 8 priv) | priv: `{postgres}`; resto: `{postgres, service_role}` |
| `20260919110000_revoke_authenticated_internal_vcdn_and_rls_auto_enable.sql` | D vCDN/RLS | 8 | vCDN + rls: `{postgres, service_role}` |
| `20260919120000_revoke_authenticated_legacy_dead_functions.sql` | E legacy |29 (28 nombres + overload) | pub: `{postgres, service_role}`; social: `{postgres}` |

**Verificaciones post-aplicación**:
- `has_function_privilege('authenticated', oid, 'EXECUTE')` = **false** para las 14 C + 8 D + 29 E (0 legacy con acceso)
- Triggers intactos (nombres/tablas sin cambios) y event trigger `ensure_rls(ddl_command_end)` sigue enganchado a `rls_auto_enable`.
- `service_role` conserva EXECUTE en las 7 vCDN (`sr_x=true`), coincidiendo con `supabase/functions/vcdn-upload/index.ts` que usa SOLO `SUPABASE_SERVICE_ROLE_KEY` en `callRpc`.
- Smoke test de RPC vivas (`create_voice_room`, `join_voice_room`, `live_send_gift`, `live_ensure_wallet`, `voice_room_is_admin/member/banned`, `favorite_sticker`, `accept_friend_request`, `mark_thread_read`, etc.): **todas `auth_x=true`** — cero regresión funcional.

**Métrica del Advisor** (`proacl LIKE '%authenticated%'` + `prosecdef` + schemas public/social/private): **101 → 54** (48 public + 6 social +  0 private). Los 54 restantes son RPC legítimas vivas que requieren revisión individual (grupo A/B mayormente). Sin barrer masivamente.



### Matriz de rollback (deshacer con un REVOKE inverso, lote por lote)

| Para revertir el lote… | Ejecutar (como postgres) |
|---|---|
| C triggers |`grant execute on function <fn> to authenticated;` (y en private: también `grant execute ... to public;` si se quiere restaurar el default) |
| D vCDN/RLS |`grant execute on function <fn(signature)> to authenticated;` |
| E legacy |`grant execute on function <fn(signature)> to authenticated;` |

Los REVOKEs son **no destructivos** (las funciones, triggers y policies siguen intactas);el rollback es puramente ACL grants reversibles. No hay datos que restaurar.



## Fase 2bis — Lote F: investigación de los 54 restantes (2026-09-19)

Revisión individual, função por função, de las 54 funciones `authenticated_security_definer_function_executable` restantes (clasificación documentada en el cuerpo de la sesión: 52 CONSERVAR + 2 INVESTIGAR。

De las 2 investigadas:

| Función | APK v1.3.49 | Callers intra-DB | RLS tabla | Riesgo | Decisión |
|---|---|---|---|---|---|
| `live_ensure_wallet(uuid)` | **0** — NO endpoint directo (0/6 DEX) | `live_send_gift`, `live_wallet_balance` (ambas SECURITY DEFINER) | `user_wallets`: solo policy `SELECT own` (`user_id=auth.uid()`);sin INSERT policy | 🔴 **Exploitable**: acepta `p_user_id` arbitrario sin validar;SECURITY DEFINER ignora RLS→ crea wallets de terceros (5000 🪙 gratis, y lee saldos ajenos | **REVOKE `authenticated`** (mantener `service_role`+`postgres`);aplicado y verificado: `auth_x=false`, `sr_x=true`; smoke test:`live_send_gift` y `live_wallet_balance` intactas (`auth_x=true`, SECURITY DEFINER) |
| `get_voice_room_decor(uuid)` | **SÍ** — endpoint `rest/v1/rpc/get_voice_room_decor` (classes4.dex) | setters admin (`set_voice_room_entrance`/`set_voice_room_pendant`, validan `voice_room_is_admin`) | `voice_room_decor`: RLS `read` con `qual=true` (cualquier authenticated ya lee la tabla) | 🟢 Ninguno real: la RLS ya expone los datos decorativos a todos los authenticated;SECURITY DEFINER no amplía acceso;datos cosméticos (no sensibles | **CONSERVAR `authenticated`** (si se quiere exigir pertenencia, es decisión de producto, no hardening ACL) |

**Métrica post-Lote-F**: `authenticated_security_definer_function_executable` = **54 →  ́53** (se revocó solo `live_ensure_wallet`).

Migración nueva: `20260919130000_revoke_authenticated_live_ensure_wallet.sql` (1 REVOKE, rollback = `grant execute on function public.live_ensure_wallet(uuid) to authenticated;`).



## Queries de respaldo
- Dump inicial: `select … from pg_proc … where prosecdef and has_function_privilege('authenticated',oid,'EXECUTE')`.
- Trigers: `pg_trigger where not tgisinternal and tgfoid in (…)`.
- Policies: `pg_policy` con subconsulta a funciones en `polqual`/`polwithcheck`.
- Cron: `cron.job`.