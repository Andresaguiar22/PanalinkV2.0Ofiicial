-- =============================================================================
-- Premium 2.0 — Fase 4.5 + 4.6: PRODUCTOS Wall Gold y PanaTV Gold
--
-- Las features 'wall' y 'panatv' ya existen en premium_features (gating hecho).
-- Faltan los PRODUCTOS para poder COMPRAR el entitlement por días.
-- Se añaden 3/5/10 días (mismo rango que Chat/Story/Live).
-- =============================================================================

insert into public.premium_products (code, feature_key, name, emoji, description, duration_days, price_coins, trial_days, sort_order) values
    ('wall_gold_3d',   'wall',   'Wall Gold 3 días',   '🧱', 'Muro con boost de visibilidad y estadísticas por 3 días',    3,  700, 0, 41),
    ('wall_gold_5d',   'wall',   'Wall Gold 5 días',   '🧱', 'Muro Gold por 5 días',                                      5, 1100, 0, 42),
    ('wall_gold_10d',  'wall',   'Wall Gold 10 días',  '🧱', 'Muro Gold por 10 días',                                    10, 2100, 0, 43),
    ('panatv_gold_3d', 'panatv', 'Pana TV Gold 3 días','📺', 'PanaTV en alta calidad y sin interrupciones por 3 días',     3,  800, 0, 51),
    ('panatv_gold_5d', 'panatv', 'Pana TV Gold 5 días','📺', 'PanaTV Gold por 5 días',                                    5, 1400, 0, 52),
    ('panatv_gold_10d','panatv', 'Pana TV Gold 10 días','📺', 'PanaTV Gold por 10 días',                                  10, 2600, 0, 53)
on conflict (code) do update
    set feature_key = excluded.feature_key,
        name = excluded.name,
        emoji = excluded.emoji,
        description = excluded.description,
        duration_days = excluded.duration_days,
        price_coins = excluded.price_coins,
        trial_days = excluded.trial_days,
        sort_order = excluded.sort_order,
        is_active = true;