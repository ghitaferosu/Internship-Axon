INSERT INTO users (
    email,
    password,
    last_name,
    first_name
)
VALUES (
           'organizer@test.com',
           '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', --parola:password
           'Test',
           'Organizer'
       )
    ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
         JOIN roles r ON r.name = 'ROLE_ORGANIZER'
WHERE u.email = 'organizer@test.com'
    ON CONFLICT (user_id, role_id) DO NOTHING;