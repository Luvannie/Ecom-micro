-- Auth Service Seed Data
-- Password: password123 (bcrypt hashed, cost factor 10)
-- Run after V1__create_auth_tables.sql

TRUNCATE user_credentials CASCADE;
TRUNCATE user_roles CASCADE;

INSERT INTO user_credentials (id, email, password_hash, enabled, created_at, updated_at) VALUES
('11111111-1111-1111-1111-111111111111', 'john.doe@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye0eH1Y5ZVOq4.x4L0Y7W1H5O5Jv5H1Kq', true, NOW(), NOW()),
('22222222-2222-2222-2222-222222222222', 'jane.smith@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye0eH1Y5ZVOq4.x4L0Y7W1H5O5Jv5H1Kq', true, NOW(), NOW()),
('33333333-3333-3333-3333-333333333333', 'bob.wilson@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye0eH1Y5ZVOq4.x4L0Y7W1H5O5Jv5H1Kq', true, NOW(), NOW()),
('44444444-4444-4444-4444-444444444444', 'alice.jones@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye0eH1Y5ZVOq4.x4L0Y7W1H5O5Jv5H1Kq', true, NOW(), NOW());

INSERT INTO user_roles (user_id, role) VALUES
('11111111-1111-1111-1111-111111111111', 'ROLE_USER'),
('22222222-2222-2222-2222-222222222222', 'ROLE_USER'),
('33333333-3333-3333-3333-333333333333', 'ROLE_USER'),
('44444444-4444-4444-4444-444444444444', 'ROLE_USER');