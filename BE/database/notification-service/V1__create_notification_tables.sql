CREATE TABLE notification_logs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    channel VARCHAR(40) NOT NULL,
    template_name VARCHAR(120) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    status VARCHAR(40) NOT NULL,
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ
);

CREATE INDEX idx_notification_logs_user_id_created_at ON notification_logs(user_id, created_at DESC);
