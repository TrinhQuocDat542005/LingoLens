-- Idempotent role seed. Admin accounts are never created with a hard-coded password.
INSERT INTO roles (name)
SELECT 'ROLE_USER' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_USER');
INSERT INTO roles (name)
SELECT 'ROLE_ADMIN' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_ADMIN');
