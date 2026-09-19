-- Lote F: revoke EXECUTE a authenticated de live_ensure_wallet
-- Hallazgo: la RPC acepta p_user_id arbitrario sin validar contra auth.uid().
-- Es SECURITY DEFINER e ignora la RLS de user_wallets (que solo permite SELECT propio)..
-- Impacto directo: un authenticated puede (a) crear wallets de terceros con 5000 monedas gratis, y (b) leer saldos de cualquier UUID.
-- El APK v1.3.49 NO la llama como endpoint directo (0 referencias en los 6 DEX).
-- Callers internos: live_send_gift y live_wallet_balance (ambas SECURITY DEFINER; no dependen del ACL de esta funcionpara invocarla).
-- Por tanto: revocar authenticated es seguro, y el acceso se restringe a service_role + postgres。 (edge functions / administracion。



revoke execute on function public.live_ensure_wallet(uuid) from authenticated;