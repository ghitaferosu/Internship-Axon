-- Seed data pentru dezvoltare locala: venues -> sections -> section_vertices
-- -> seats -> performers -> events -> events_performers -> tickets.
--
-- Adaptat pentru schema de dupa V10: sectiunile nu mai au `direction`, ci un
-- `angle` in radiani, iar conturul desenat traieste in `section_vertices`.
--
-- Ruleaza DUPA ce aplicatia a pornit cel putin o data (Flyway creeaza schema).
-- Scriptul este idempotent: rularea repetata nu duplica nimic.
--
--   docker exec -i api-gateway-postgres-1 psql -U user -d event_db < scripts/seed-dev-data.sql

SET client_encoding = 'UTF8';

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. VENUES
-- no_seats intra cu 0 si e recalculat la pasul 6 din locurile chiar generate.
-- ---------------------------------------------------------------------------
INSERT INTO venues (name, address, no_seats)
VALUES
    ('Arena Nationala',  'Bd. Basarabia 37, Bucuresti',        0),
    ('Sala Palatului',   'Str. Ion Campineanu 28, Bucuresti',  0),
    ('BT Arena',         'Aleea Stadionului 2, Cluj-Napoca',   0)
ON CONFLICT (name, address) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. SECTIONS
-- `angle` intra cu 0 si e calculat la pasul 4 din centroidul poligonului,
-- ca sa nu poata ajunge desincronizat fata de forma desenata.
-- Nu exista constrangere unica pe (venue_id, section_name) -> NOT EXISTS.
-- ---------------------------------------------------------------------------
INSERT INTO sections (venue_id, section_name, angle,
                      row_start, row_end, seat_start, seat_end,
                      row_spacing, origin_x, origin_y, col_spacing)
SELECT v.id, s.section_name, 0,
       s.row_start, s.row_end, s.seat_start, s.seat_end,
       0, 0, 0, 0
FROM (VALUES
    ('Arena Nationala', 'Tribuna Nord', 1, 10, 1, 20),
    ('Arena Nationala', 'Tribuna Sud',  1, 10, 1, 20),
    ('Arena Nationala', 'Peluza Est',   1,  8, 1, 15),
    ('Arena Nationala', 'Peluza Vest',  1,  8, 1, 15),
    ('Sala Palatului',  'Parter',       1,  8, 1, 15),
    ('Sala Palatului',  'Balcon',       1,  5, 1, 12),
    ('BT Arena',        'Sector A',     1,  6, 1, 12),
    ('BT Arena',        'Sector B',     1,  6, 1, 12),
    ('BT Arena',        'Sector C',     1,  6, 1, 12)
) AS s(venue_name, section_name, row_start, row_end, seat_start, seat_end)
JOIN venues v ON v.name = s.venue_name
WHERE NOT EXISTS (
    SELECT 1 FROM sections x
    WHERE x.venue_id = v.id AND x.section_name = s.section_name
);

-- ---------------------------------------------------------------------------
-- 3. SECTION_VERTICES
-- Conturul desenat al fiecarei sectiuni, ca dreptunghi in jurul scenei (0,0).
-- Definim doar bounding box-ul; cele 4 colturi se genereaza in ordine
-- antiorara, cum se asteapta frontend-ul la un poligon.
-- ---------------------------------------------------------------------------
INSERT INTO section_vertices (section_id, vertex_order, x, y)
SELECT sec.id, c.vertex_order, c.x, c.y
FROM (VALUES
    ('Arena Nationala', 'Tribuna Nord', -200.0,  150.0,  200.0,  350.0),
    ('Arena Nationala', 'Tribuna Sud',  -200.0, -350.0,  200.0, -150.0),
    ('Arena Nationala', 'Peluza Est',    150.0, -150.0,  350.0,  150.0),
    ('Arena Nationala', 'Peluza Vest',  -350.0, -150.0, -150.0,  150.0),
    ('Sala Palatului',  'Parter',       -150.0,   80.0,  150.0,  260.0),
    ('Sala Palatului',  'Balcon',       -120.0,  300.0,  120.0,  420.0),
    ('BT Arena',        'Sector A',     -160.0,  120.0,  160.0,  280.0),
    ('BT Arena',        'Sector B',      140.0, -120.0,  300.0,  120.0),
    ('BT Arena',        'Sector C',     -300.0, -120.0, -140.0,  120.0)
) AS box(venue_name, section_name, x1, y1, x2, y2)
JOIN venues   v   ON v.name = box.venue_name
JOIN sections sec ON sec.venue_id = v.id AND sec.section_name = box.section_name
CROSS JOIN LATERAL (VALUES
    (0, box.x1, box.y1),
    (1, box.x2, box.y1),
    (2, box.x2, box.y2),
    (3, box.x1, box.y2)
) AS c(vertex_order, x, y)
ON CONFLICT (section_id, vertex_order) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. Calculeaza `angle` din centroidul poligonului
-- Unghiul e masurat de la scena (0,0) catre centrul sectiunii, in radiani.
-- ---------------------------------------------------------------------------
UPDATE sections s
SET angle = c.angle
FROM (
    SELECT section_id, atan2(avg(y), avg(x)) AS angle
    FROM section_vertices
    GROUP BY section_id
) c
WHERE s.id = c.section_id
  AND s.angle IS DISTINCT FROM c.angle;

-- ---------------------------------------------------------------------------
-- 5. SEATS
-- Produsul cartezian rand x loc, derivat din intervalele fiecarei sectiuni.
-- ---------------------------------------------------------------------------
INSERT INTO seats (section_id, row_number, seat_number)
SELECT sec.id, gr.row_no, gs.seat_no
FROM sections sec
CROSS JOIN LATERAL generate_series(sec.row_start,  sec.row_end)  AS gr(row_no)
CROSS JOIN LATERAL generate_series(sec.seat_start, sec.seat_end) AS gs(seat_no)
ON CONFLICT (section_id, row_number, seat_number) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 6. Sincronizeaza venues.no_seats cu numarul real de locuri
-- ---------------------------------------------------------------------------
UPDATE venues v
SET no_seats = agg.total
FROM (
    SELECT sec.venue_id, COUNT(s.id) AS total
    FROM sections sec
    JOIN seats s ON s.section_id = sec.id
    GROUP BY sec.venue_id
) agg
WHERE v.id = agg.venue_id
  AND v.no_seats IS DISTINCT FROM agg.total;

-- ---------------------------------------------------------------------------
-- 7. PERFORMERS
-- ---------------------------------------------------------------------------
INSERT INTO performers (name, genre, description)
SELECT p.name, p.genre, p.description
FROM (VALUES
    ('Coldplay',            'Alternative Rock', 'Trupa britanica de rock alternativ.'),
    ('Radiohead',           'Alternative Rock', 'Trupa britanica experimentala.'),
    ('The Rolling Stones',  'Rock',             'Trupa britanica de rock clasic.'),
    ('Subcarpati',          'Hip-Hop',          'Proiect romanesc de hip-hop cu influente folclorice.'),
    ('Zdob si Zdub',        'Folk Rock',        'Trupa din Republica Moldova.'),
    ('Delia',               'Pop',              'Artista pop din Romania.')
) AS p(name, genre, description)
WHERE NOT EXISTS (
    SELECT 1 FROM performers x WHERE x.name = p.name
);

-- ---------------------------------------------------------------------------
-- 8. EVENTS
-- ---------------------------------------------------------------------------
INSERT INTO events (venue_id, name, country, city, event_start_hour, status)
SELECT v.id, e.name, e.country, e.city, e.start_hour::timestamptz, e.status
FROM (VALUES
    ('Arena Nationala', 'Rock the City 2026',   'Romania', 'Bucuresti',   '2026-06-15 20:00:00+03', 'LIVE'),
    ('Arena Nationala', 'Summer Fest 2026',     'Romania', 'Bucuresti',   '2026-07-20 19:30:00+03', 'LIVE'),
    ('Sala Palatului',  'Jazz in the Park',     'Romania', 'Bucuresti',   '2026-09-05 19:00:00+03', 'LIVE'),
    ('BT Arena',        'Untold Warm-Up',       'Romania', 'Cluj-Napoca', '2026-08-01 18:00:00+03', 'LIVE'),
    ('BT Arena',        'Electric Castle Night','Romania', 'Cluj-Napoca', '2026-08-10 21:00:00+03', 'SETTING_UP'),
    ('Sala Palatului',  'Winter Gala 2026',     'Romania', 'Bucuresti',   '2026-12-20 18:30:00+02', 'DRAFT'),

    ('Arena Nationala', 'Stadium Nights I',     'Romania', 'Bucuresti',   '2026-05-02 20:00:00+03', 'LIVE'),
    ('Arena Nationala', 'Stadium Nights II',    'Romania', 'Bucuresti',   '2026-05-16 20:00:00+03', 'LIVE'),
    ('Arena Nationala', 'Bucharest Open Air',   'Romania', 'Bucuresti',   '2026-05-30 19:00:00+03', 'LIVE'),
    ('Arena Nationala', 'Summer Kickoff',       'Romania', 'Bucuresti',   '2026-06-06 19:30:00+03', 'LIVE'),
    ('Arena Nationala', 'Arena Live Session',   'Romania', 'Bucuresti',   '2026-06-27 20:30:00+03', 'LIVE'),
    ('Arena Nationala', 'Autumn Arena Fest',    'Romania', 'Bucuresti',   '2026-09-19 19:00:00+03', 'LIVE'),
    ('Arena Nationala', 'Capital Rock Night',   'Romania', 'Bucuresti',   '2026-10-03 20:00:00+03', 'LIVE'),
    ('Arena Nationala', 'Year End Arena Show',  'Romania', 'Bucuresti',   '2026-11-14 19:30:00+02', 'SETTING_UP'),

    ('Sala Palatului',  'Piano Recital Series', 'Romania', 'Bucuresti',   '2026-05-09 19:00:00+03', 'LIVE'),
    ('Sala Palatului',  'Chamber Music Evening','Romania', 'Bucuresti',   '2026-05-23 18:30:00+03', 'LIVE'),
    ('Sala Palatului',  'Jazz Standards Night', 'Romania', 'Bucuresti',   '2026-06-13 20:00:00+03', 'LIVE'),
    ('Sala Palatului',  'Folk Revival',         'Romania', 'Bucuresti',   '2026-07-04 19:00:00+03', 'LIVE'),
    ('Sala Palatului',  'Acoustic Sessions',    'Romania', 'Bucuresti',   '2026-07-18 19:30:00+03', 'LIVE'),
    ('Sala Palatului',  'Symphonic Pop',        'Romania', 'Bucuresti',   '2026-09-12 19:00:00+03', 'LIVE'),
    ('Sala Palatului',  'Blues Legends',        'Romania', 'Bucuresti',   '2026-10-17 20:00:00+03', 'LIVE'),
    ('Sala Palatului',  'Christmas Concert',    'Romania', 'Bucuresti',   '2026-12-12 18:00:00+02', 'SETTING_UP'),

    ('BT Arena',        'Cluj Indie Night',     'Romania', 'Cluj-Napoca', '2026-05-08 20:00:00+03', 'LIVE'),
    ('BT Arena',        'Transilvania Beats',   'Romania', 'Cluj-Napoca', '2026-05-22 21:00:00+03', 'LIVE'),
    ('BT Arena',        'Electronic Nights',    'Romania', 'Cluj-Napoca', '2026-06-20 22:00:00+03', 'LIVE'),
    ('BT Arena',        'Hip-Hop Summit',       'Romania', 'Cluj-Napoca', '2026-07-11 20:00:00+03', 'LIVE'),
    ('BT Arena',        'Cluj Rock Marathon',   'Romania', 'Cluj-Napoca', '2026-08-22 18:00:00+03', 'LIVE'),
    ('BT Arena',        'Student Fest',         'Romania', 'Cluj-Napoca', '2026-09-26 19:00:00+03', 'LIVE'),
    ('BT Arena',        'Winter Sounds',        'Romania', 'Cluj-Napoca', '2026-11-07 19:30:00+02', 'LIVE'),
    ('BT Arena',        'New Year Warm-Up',     'Romania', 'Cluj-Napoca', '2026-12-28 21:00:00+02', 'DRAFT')
) AS e(venue_name, name, country, city, start_hour, status)
JOIN venues v ON v.name = e.venue_name
WHERE NOT EXISTS (
    SELECT 1 FROM events x
    WHERE x.name = e.name
      AND x.venue_id = v.id
      AND x.event_start_hour = e.start_hour::timestamptz
);

-- ---------------------------------------------------------------------------
-- 9. EVENTS_PERFORMERS
-- ---------------------------------------------------------------------------
INSERT INTO events_performers (event_id, performer_id, start_hour, end_hour)
SELECT ev.id,
       p.id,
       ev.event_start_hour + ap.offset_start::interval,
       ev.event_start_hour + ap.offset_end::interval
FROM (VALUES
    ('Rock the City 2026',    'Coldplay',           '0 hours',  '2 hours'),
    ('Rock the City 2026',    'Radiohead',          '2 hours',  '4 hours'),
    ('Summer Fest 2026',      'The Rolling Stones', '0 hours',  '2 hours'),
    ('Summer Fest 2026',      'Delia',              '2 hours',  '3 hours'),
    ('Jazz in the Park',      'Subcarpati',         '0 hours',  '90 minutes'),
    ('Untold Warm-Up',        'Zdob si Zdub',       '0 hours',  '2 hours'),
    ('Untold Warm-Up',        'Subcarpati',         '2 hours',  '4 hours'),
    ('Electric Castle Night', 'Coldplay',           '0 hours',  '2 hours'),

    ('Stadium Nights I',      'Coldplay',           '0 hours',  '2 hours'),
    ('Stadium Nights II',     'Radiohead',          '0 hours',  '2 hours'),
    ('Bucharest Open Air',    'The Rolling Stones', '0 hours',  '2 hours'),
    ('Summer Kickoff',        'Delia',              '0 hours',  '90 minutes'),
    ('Arena Live Session',    'Subcarpati',         '0 hours',  '90 minutes'),
    ('Autumn Arena Fest',     'Zdob si Zdub',       '0 hours',  '2 hours'),
    ('Capital Rock Night',    'Radiohead',          '0 hours',  '2 hours'),
    ('Year End Arena Show',   'Coldplay',           '0 hours',  '2 hours'),

    ('Piano Recital Series',  'Delia',              '0 hours',  '90 minutes'),
    ('Chamber Music Evening', 'Delia',              '0 hours',  '90 minutes'),
    ('Jazz Standards Night',  'Subcarpati',         '0 hours',  '2 hours'),
    ('Folk Revival',          'Zdob si Zdub',       '0 hours',  '2 hours'),
    ('Acoustic Sessions',     'Subcarpati',         '0 hours',  '90 minutes'),
    ('Symphonic Pop',         'Delia',              '0 hours',  '2 hours'),
    ('Blues Legends',         'The Rolling Stones', '0 hours',  '2 hours'),
    ('Christmas Concert',     'Delia',              '0 hours',  '90 minutes'),

    ('Cluj Indie Night',      'Radiohead',          '0 hours',  '2 hours'),
    ('Transilvania Beats',    'Zdob si Zdub',       '0 hours',  '2 hours'),
    ('Electronic Nights',     'Subcarpati',         '0 hours',  '3 hours'),
    ('Hip-Hop Summit',        'Subcarpati',         '0 hours',  '2 hours'),
    ('Cluj Rock Marathon',    'The Rolling Stones', '0 hours',  '3 hours'),
    ('Student Fest',          'Delia',              '0 hours',  '2 hours'),
    ('Winter Sounds',         'Zdob si Zdub',       '0 hours',  '2 hours'),
    ('New Year Warm-Up',      'Coldplay',           '0 hours',  '2 hours')
) AS ap(event_name, performer_name, offset_start, offset_end)
JOIN events     ev ON ev.name = ap.event_name
JOIN performers p  ON p.name  = ap.performer_name
ON CONFLICT (event_id, performer_id, start_hour) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 10. TICKETS
-- Cate un bilet pentru fiecare loc din venue-ul evenimentului, doar pentru
-- evenimentele LIVE.
-- ---------------------------------------------------------------------------
INSERT INTO tickets (event_id, seat_id, status, price_cents, created_at, updated_at)
SELECT ev.id, s.id, 'AVAILABLE', COALESCE(pr.price_cents, 15000), now(), now()
FROM events ev
JOIN sections sec ON sec.venue_id = ev.venue_id
JOIN seats    s   ON s.section_id = sec.id
LEFT JOIN (VALUES
    ('Rock the City 2026',    25000),
    ('Summer Fest 2026',      18000),
    ('Jazz in the Park',      12000),
    ('Untold Warm-Up',        20000)
) AS pr(event_name, price_cents) ON pr.event_name = ev.name
WHERE ev.status = 'LIVE'
ON CONFLICT (event_id, seat_id) DO NOTHING;

COMMIT;

-- ---------------------------------------------------------------------------
-- Sumar
-- ---------------------------------------------------------------------------
SELECT 'venues' AS tabela, COUNT(*) FROM venues
UNION ALL SELECT 'sections',          COUNT(*) FROM sections
UNION ALL SELECT 'section_vertices',  COUNT(*) FROM section_vertices
UNION ALL SELECT 'seats',             COUNT(*) FROM seats
UNION ALL SELECT 'performers',        COUNT(*) FROM performers
UNION ALL SELECT 'events',            COUNT(*) FROM events
UNION ALL SELECT 'events_performers', COUNT(*) FROM events_performers
UNION ALL SELECT 'tickets',           COUNT(*) FROM tickets;
