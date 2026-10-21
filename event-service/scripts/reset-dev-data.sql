-- ATENTIE: sterge TOATE datele din tabelele de domeniu si reseteaza secventele
-- de id. Schema (si istoricul Flyway) raman intacte.
--
-- Ordinea tabelelor nu conteaza tehnic (TRUNCATE le goleste pe toate deodata,
-- iar CASCADE se ocupa de cheile straine), dar e scrisa dinspre copii spre
-- parinti ca sa se citeasca usor cine depinde de cine.
--
--   docker exec -i api-gateway-postgres-1 psql -U user -d event_db < scripts/reset-dev-data.sql

BEGIN;

TRUNCATE TABLE payments,
               tickets,
               events_performers,
               events,
               seats,
               section_vertices,
               sections,
               performers,
               venues
    RESTART IDENTITY CASCADE;

COMMIT;

-- Verificare: toate trebuie sa fie 0.
SELECT 'venues' AS tabela, COUNT(*) FROM venues
UNION ALL SELECT 'sections',          COUNT(*) FROM sections
UNION ALL SELECT 'section_vertices',  COUNT(*) FROM section_vertices
UNION ALL SELECT 'seats',             COUNT(*) FROM seats
UNION ALL SELECT 'performers',        COUNT(*) FROM performers
UNION ALL SELECT 'events',            COUNT(*) FROM events
UNION ALL SELECT 'events_performers', COUNT(*) FROM events_performers
UNION ALL SELECT 'tickets',           COUNT(*) FROM tickets
UNION ALL SELECT 'payments',          COUNT(*) FROM payments;
