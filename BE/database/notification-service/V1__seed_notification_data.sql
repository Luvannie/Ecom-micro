-- Notification Service Seed Data
-- Run after V1__create_notification_tables.sql

TRUNCATE notification_logs CASCADE;

INSERT INTO notification_logs (id, user_id, channel, template_name, recipient, subject, status, failure_reason, created_at, sent_at) VALUES
('11111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'EMAIL', 'order_confirmation', 'john.doe@example.com', 'Order Confirmed - #11111111', 'SENT', NULL, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
('22222222-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222222', 'EMAIL', 'order_delivered', 'jane.smith@example.com', 'Order Delivered - #22222222', 'SENT', NULL, NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days'),
('33333333-3333-3333-3333-333333333333', '33333333-3333-3333-3333-333333333333', 'SMS', 'order_pending', '+84-905-345-678', NULL, 'SENT', NULL, NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour');