-- Regalos premium estilo TikTok (galaxia, leones, tigres, aviones...)
-- Añadidos al catálogo real public.live_gifts. La verificación y el descuento
-- de monedas los hace public.live_send_gift (ver 20260916000000_live_engagement.sql);
-- aquí solo se pobla el catálogo y se garantiza la emisión realtime del evento.

insert into public.live_gifts (code, name, emoji, coins, sort_order) values
    ('galaxy',      'Galaxia',     '🌌', 5000,  80),
    ('lion',        'León',        '🦁', 3000,  90),
    ('tiger',       'Tigre',       '🐯', 3000, 100),
    ('airplane',    'Avión',       '✈️', 2000, 110),
    ('submarine',   'Submarino',   '🚢', 2000, 120)
on conflict (code) do update
    set name = excluded.name,
        emoji = excluded.emoji,
        coins = excluded.coins,
        sort_order = excluded.sort_order,
        is_active = true;