-- Seed initial roles
INSERT INTO roles (name) VALUES ('ROLE_USER');
INSERT INTO roles (name) VALUES ('ROLE_ADMIN');

-- Seed initial Admin user (email: admin@lingolens.com, password: admin123)
-- BCrypt hash of 'admin123' is '$2a$10$DGP6auoXlBPCZ9HkEpyWGe.Yn3g20110jR67kMpeBfXN4y2aQo1f2'
INSERT INTO users (email, password, name, target_level, streak_days) 
VALUES ('admin@lingolens.com', '$2a$10$DGP6auoXlBPCZ9HkEpyWGe.Yn3g20110jR67kMpeBfXN4y2aQo1f2', 'Administrator', 'B2', 0);

-- Assign ROLE_ADMIN and ROLE_USER to admin user
INSERT INTO user_roles (user_id, role_id) 
VALUES (
    (SELECT id FROM users WHERE email = 'admin@lingolens.com'),
    (SELECT id FROM roles WHERE name = 'ROLE_ADMIN')
);
INSERT INTO user_roles (user_id, role_id) 
VALUES (
    (SELECT id FROM users WHERE email = 'admin@lingolens.com'),
    (SELECT id FROM roles WHERE name = 'ROLE_USER')
);
