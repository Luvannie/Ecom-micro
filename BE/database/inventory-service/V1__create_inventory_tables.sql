CREATE TABLE product_stock (
    product_id UUID PRIMARY KEY,
    available_quantity INT NOT NULL,
    reserved_quantity INT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE stock_reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE stock_reservation_items (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES stock_reservations(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    quantity INT NOT NULL
);

CREATE INDEX idx_stock_reservation_items_reservation_id ON stock_reservation_items(reservation_id);

CREATE TABLE stock_audit_logs (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    change_type VARCHAR(60) NOT NULL,
    quantity_delta INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
