-- User Service Seed Data
-- Run after V1__create_user_tables.sql

TRUNCATE user_profiles CASCADE;
TRUNCATE user_addresses CASCADE;

INSERT INTO user_profiles (id, email, display_name, phone, preferences, created_at, updated_at) VALUES
('11111111-1111-1111-1111-111111111111', 'john.doe@example.com', 'John Doe', '+84-903-123-456', '{"notifications":true,"language":"vi"}', NOW(), NOW()),
('22222222-2222-2222-2222-222222222222', 'jane.smith@example.com', 'Jane Smith', '+84-904-234-567', '{"notifications":true,"language":"en"}', NOW(), NOW()),
('33333333-3333-3333-3333-333333333333', 'bob.wilson@example.com', 'Bob Wilson', '+84-905-345-678', '{"notifications":false,"language":"vi"}', NOW(), NOW()),
('44444444-4444-4444-4444-444444444444', 'alice.jones@example.com', 'Alice Jones', '+84-906-456-789', '{"notifications":true,"language":"en"}', NOW(), NOW());

INSERT INTO user_addresses (id, user_id, recipient_name, phone, line1, line2, city, district, postal_code, is_default, created_at, updated_at) VALUES
('aaaa1111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'John Doe', '+84-903-123-456', '123 Nguyen Hue Street', 'District 1', 'Ho Chi Minh City', 'District 1', '700000', true, NOW(), NOW()),
('aaaa2222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'John Doe', '+84-903-123-456', '456 Le Duan Street', 'Tan Binh District', 'Ho Chi Minh City', 'Tan Binh', '700000', false, NOW(), NOW()),
('aaaa3333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222', 'Jane Smith', '+84-904-234-567', '789 Dong Khoi Street', 'District 1', 'Ho Chi Minh City', 'District 1', '700000', true, NOW(), NOW()),
('aaaa4444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333333', 'Bob Wilson', '+84-905-345-678', '321 Tran Hung Dao Street', 'District 5', 'Ho Chi Minh City', 'District 5', '700000', true, NOW(), NOW());