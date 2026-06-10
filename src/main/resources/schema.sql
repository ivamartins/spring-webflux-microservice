-- Schema bootstrap for Postgres.
-- Loaded automatically by Spring at startup (R2DBC init).
CREATE TABLE IF NOT EXISTS orders (
    id          UUID PRIMARY KEY,
    customer_id VARCHAR(64)   NOT NULL,
    amount      NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    currency    CHAR(3)       NOT NULL,
    status      VARCHAR(32)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_orders_customer_id ON orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_orders_status     ON orders(status);
