# send-push — estado del deploy (2026-08-25)

## Estado: DESPLEGADO, falta 1 secreto

| Pieza | Estado |
|---|---|
| Funcion `send-push` v9 (FCM v1, hibrido notification+data) | ✅ Desplegada, `verify_jwt=false` |
| Secreto `EDGE_INTERNAL_SECRET` (edge) | ✅ Configurado |
| Secreto en DB `private.edge_secrets` (lo leen los triggers) | ✅ Configurado |
| Triggers con payload rico + preview E2EE-safe | ✅ Aplicados y verificados |
| `FIREBASE_SERVICE_ACCOUNT` | ❌ **FALTA** — sin esto la funcion responde 500 |

## Por que no sonaba nada con la app cerrada (causas raiz, TODAS reales)

1. El GUC `app.edge_secret` era NULL -> los 3 triggers salian en silencio
   (`if v_edge_secret is null then return new`). Nunca se hizo ni una llamada HTTP.
2. La funcion vieja tenia `verify_jwt=true` y los triggers no mandan JWT ->
   401 en el gateway.
3. La funcion vieja esperaba formato Database Webhook (`{type:"INSERT"}`) y
   los triggers mandan `{user_id,title,body}`.
4. `notification-dispatcher` usa la API legacy de FCM (apagada por Google en 2024).
5. Los custom GUC `app.*` requieren superuser; la API de queries no los deja
   fijar -> por eso el secreto vive ahora en `private.edge_secrets` (tabla sin
   acceso para roles API, leida por la funcion security definer
   `private.get_edge_secret()`).

## Paso final (requiere Firebase Console)

1. Firebase Console -> ⚙️ Configuracion del proyecto -> **Cuentas de servicio**
   -> **Generar nueva clave privada** -> descargar el JSON.
2. Configurar el secreto:

```bash
supabase secrets set FIREBASE_SERVICE_ACCOUNT='<contenido completo del JSON>' \
  --project-ref tivqjfgjdxgzicrridaz
```

(o dashboard: Project Settings -> Edge Functions -> Secrets -> Add).

3. Verificar (el secreto interno esta en `private.edge_secrets`):

```bash
curl -X POST https://tivqjfgjdxgzicrridaz.functions.supabase.co/send-push \
  -H 'Content-Type: application/json' \
  -H 'x-internal-secret: <edge_secret>' \
  -d '{"user_id":"<tu user id>","title":"Test","body":"Suena con app cerrada"}'
```

Debe responder `{"status":"OK","sent":1}` y el telefono con la app **cerrada**
debe sonar y mostrar la notificacion.

## Rotacion del secreto interno

Hay que actualizar los DOS lados (tabla DB + secreto edge) con el mismo valor.
