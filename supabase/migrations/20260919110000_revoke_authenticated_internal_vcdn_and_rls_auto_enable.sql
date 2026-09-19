-- Lote D: revoke EXECUTE a authenticated de funciones internas de vCDN y del event trigger rls_auto_enable.
-- Verificado en supabase/functions/vcdn-upload/index.ts: callRpc usa SOLO SUPABASE_SERVICE_ROLE_KEY (lineas 50-59).
-- rls_auto_enable es event trigger (evt=ensure_rls(ddl_command_end): solo lo invoca el motor de Postgres en DDL.

revoke execute on function public.claim_vcdn_complete(text, text) from authenticated;
revoke execute on function public.claim_vcdn_upload_session(text, text, text, text, text, bigint) from authenticated;
revoke execute on function public.fail_vcdn_complete_claim(text) from authenticated;
revoke execute on function public.fail_vcdn_upload_session(text, text, text, text) from authenticated;
revoke execute on function public.finalize_vcdn_session(text, text, text, boolean) from authenticated;
revoke execute on function public.populate_vcdn_upload_session(text, text, text, text, text, text) from authenticated;
revoke execute on function public.update_vcdn_upload_bytes(text, bigint) from authenticated;
revoke execute on function public.rls_auto_enable() from authenticated;