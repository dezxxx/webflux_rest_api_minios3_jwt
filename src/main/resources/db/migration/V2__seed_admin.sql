-- Development-only administrator.
--
-- POST /auth/register always assigns role = USER, otherwise anyone could grant
-- themselves ADMIN and authorisation would be meaningless. That leaves nobody able
-- to create the first administrator, so it is seeded here — without it none of the
-- ADMIN-only endpoints can be exercised, in Swagger or in integration tests.
--
-- Credentials: admin / admin. The hash is bcrypt with cost 10; the salt is stored
-- inside the string itself. Change this password in any real environment.
INSERT INTO users (username, password_hash, role, status)
VALUES ('admin',
        '$2a$10$YcSO5fcQZxGbOY9SGAGqSOuasayNQTHSXtSFOGFoTQtgcOh/nyq2K',
        'ADMIN',
        'ACTIVE');