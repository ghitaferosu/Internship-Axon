-- V3's bcrypt hash for organizer@test.com did not actually correspond to
-- the password "password" (the inline comment was wrong/copied from
-- elsewhere and never verified). This replaces it with a hash that was
-- verified to match "password" before being committed.
UPDATE users
SET password = '$2a$10$JdAORGLpv/2XAT0bAkk9rOTYxElFNDC7kpzeGC.H9zsBdUPRG1WLS'
WHERE email = 'organizer@test.com';
