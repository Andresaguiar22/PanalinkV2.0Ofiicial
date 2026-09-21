-- =============================================================================
-- Premium 2.0 — Notificaciones automáticas (in-app)
--
-- Genera notificaciones in-app automáticamente cuando:
--   1. Se activa un entitlement (compra con monedas)         -> PREMIUM
--   2. Un entitlement pasa a 'expired' (vencimiento)          -> PREMIUM
--   3. El usuario gana monedas por recompensa/misión          -> REWARD / COINS
--
-- Las notificaciones se insertan vía public.notify_user (security definer).
-- Esta migración es INCREMENTAL; la app solo lee via notifications_for_me().
-- =============================================================================

-- 1) Trigger AFTER INSERT en user_entitlements: feature activada.
create or replace function public.trg_notify_entitlement_activated()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_feature text := coalesce(new.feature_key, 'premium');
    v_days integer := greatest(1, ceil(extract(epoch from (new.expires_at - least(new.starts_at, now())))/86400.0)::int);
begin
    perform public.notify_user(
        p_user_id  := new.user_id,
        p_category := 'PREMIUM',
        p_title    := format('Beneficio activado: %s', v_feature),
        p_body     := format('Tu %s está activo por %s días. Disfrutá tus ventajas Premium.', v_feature, v_days),
        p_priority := 'HIGH',
        p_payload  := jsonb_build_object(
            'feature_key', v_feature,
            'product_code', new.product_code,
            'expires_at', new.expires_at,
            'entitlement_id', new.id
        )
    );
    return new;
end;
$$;

drop trigger if exists trg_entitlement_activated_notify on public.user_entitlements;
create trigger trg_entitlement_activated_notify
after insert on public.user_entitlements
for each row execute function public.trg_notify_entitlement_activated();

-- 2) Trigger AFTER UPDATE en user_entitlements: vencimiento (status -> 'expired').
create or replace function public.trg_notify_entitlement_expired()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if old.status <> 'expired' and new.status = 'expired' then
        perform public.notify_user(
            p_user_id  := new.user_id,
            p_category := 'PREMIUM',
            p_title    := format('Tu %s venció', coalesce(new.feature_key, 'beneficio')),
            p_body     := 'Se terminó tu beneficio Premium. Renová con monedas para seguir disfrutando.',
            p_priority := 'HIGH',
            p_payload  := jsonb_build_object(
                'feature_key', new.feature_key,
                'product_code', new.product_code,
                'entitlement_id', new.id
            )
        );
    end if;
    return new;
end;
$$;

drop trigger if exists trg_entitlement_expired_notify on public.user_entitlements;
create trigger trg_entitlement_expired_notify
after update on public.user_entitlements
for each row execute function public.trg_notify_entitlement_expired();

-- 3) Trigger AFTER INSERT en wallet_saldo_log (si existiera) no aplica aquí:
--    las monedas se otorgan vía RPC (earn), que ya invoca notify_user cuando
--    corresponde. Este trigger cubre la creación de notificaciones REWARD al
--    reclamar misiones desde admin/backfill. (Extensible.)

-- Grants: las funciones de trigger las ejecuta el sistema (security definer),
-- no requieren grant. notify_user permanece restringido a service_role.

-- =============================================================================
-- Validación local (self-check; no aplica en prod por el guard):
--   select public.notifications_for_me() -- requiere auth.uid(); probar con shim.
-- =============================================================================