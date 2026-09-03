-- Salas de Voz — multi-room hardening.
-- La Fase 1.2 original creó un índice singleton para el lobby de prototipo.
-- Panalink ahora admite múltiples salas activas simultáneamente: cada sala tiene
-- su propio UUID y sus propios miembros/sillones/señalización.
-- Este parche es idempotente y NO toca tablas del chat.

drop index if exists public.voice_rooms_single_live_room;

-- Mantener el índice de listado para descubrir rápidamente todas las salas activas.
create index if not exists voice_rooms_live_idx
  on public.voice_rooms (status, created_at desc)
  where status = 'live';

-- Garantías de integridad que deben sobrevivir a cualquier instalación anterior.
alter table public.voice_room_seats replica identity full;
alter table public.voice_room_members replica identity full;

-- Una sala cerrada nunca vuelve a live por un UPDATE directo desde el cliente:
-- las mutaciones de estado siguen pasando por RPC/owner.
