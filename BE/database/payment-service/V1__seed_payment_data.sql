-- Payment Service Seed Data
-- Run after V1__create_payment_tables.sql

TRUNCATE payments CASCADE;
TRUNCATE payment_webhook_events CASCADE;
TRUNCATE outbox_events CASCADE;

INSERT INTO payments (id, order_id, user_id, amount, currency, status, provider_payment_id, idempotency_key, created_at, updated_at) VALUES
('11111111-1111-1111-1111-111111111111', '11111111-aaaa-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 34.96, 'USD', 'COMPLETED', 'pay_stripe_123456', 'idem-1111111', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
('22222222-2222-2222-2222-222222222222', '22222222-bbbb-2222-2222-222222222222', '22222222-2222-2222-2222-222222222222', 51.46, 'USD', 'COMPLETED', 'pay_stripe_789012', 'idem-2222222', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days');

INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status, created_at, published_at) VALUES
('11111111-aaaa-aaaa-aaaa-111111111111', 'Payment', '11111111-1111-1111-1111-111111111111', 'PaymentCompleted', '{"paymentId":"11111111-1111-1111-1111-111111111111","orderId":"11111111-aaaa-1111-1111-111111111111","amount":34.96}', 'PUBLISHED', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
('22222222-bbbb-bbbb-bbbb-222222222222', 'Payment', '22222222-2222-2222-2222-222222222222', 'PaymentCompleted', '{"paymentId":"22222222-2222-2222-2222-222222222222","orderId":"22222222-bbbb-2222-2222-222222222222","amount":51.46}', 'PUBLISHED', NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days');