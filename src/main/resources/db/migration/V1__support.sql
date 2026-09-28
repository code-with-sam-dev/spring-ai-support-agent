-- Fictional payments data for a support copilot. Card numbers are published
-- test numbers; the legacy column holds them in full so the boundary that keeps
-- them out of every tool result has something real to protect.
CREATE TABLE customers (
    id    TEXT PRIMARY KEY,
    name  TEXT NOT NULL,
    email TEXT NOT NULL
);

CREATE TABLE payments (
    id           TEXT PRIMARY KEY,
    customer_id  TEXT NOT NULL REFERENCES customers (id),
    order_ref    TEXT NOT NULL,
    merchant     TEXT NOT NULL,
    amount_cents BIGINT NOT NULL,
    currency     TEXT NOT NULL,
    card_number  TEXT NOT NULL,
    status       TEXT NOT NULL,
    captured_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE refunds (
    id           UUID PRIMARY KEY,
    payment_id   TEXT NOT NULL REFERENCES payments (id),
    customer_id  TEXT NOT NULL REFERENCES customers (id),
    amount_cents BIGINT NOT NULL,
    reason       TEXT NOT NULL,
    status       TEXT NOT NULL,
    requested_by TEXT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    approved_by  TEXT,
    executed_at  TIMESTAMPTZ
);
-- One open request per payment: asking twice returns the same refund.
CREATE UNIQUE INDEX one_open_refund_per_payment ON refunds (payment_id)
    WHERE status IN ('PENDING', 'EXECUTING');

CREATE TABLE refund_audit (
    id        BIGSERIAL PRIMARY KEY,
    refund_id UUID NOT NULL,
    event     TEXT NOT NULL,
    actor     TEXT NOT NULL,
    at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    detail    TEXT
);

-- Stands in for the card network. A row here is money that actually moved.
CREATE TABLE provider_calls (
    id        BIGSERIAL PRIMARY KEY,
    refund_id UUID NOT NULL,
    at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO customers VALUES
    ('CUST-17', 'Maya Okafor', 'maya@example.com'),
    ('CUST-42', 'Tom Lindqvist', 'tom@example.com');

INSERT INTO payments VALUES
    ('PAY-1043-A', 'CUST-17', '1043', 'Northwind Books', 4900, 'USD', '4242424242424242', 'CAPTURED', now() - interval '2 days'),
    ('PAY-1043-B', 'CUST-17', '1043', 'Northwind Books', 4900, 'USD', '4242424242424242', 'CAPTURED', now() - interval '2 days' + interval '40 seconds'),
    ('PAY-1051',   'CUST-17', '1051', 'Contoso Coffee',   650, 'USD', '4242424242424242', 'CAPTURED', now() - interval '1 day'),
    ('PAY-2210',   'CUST-42', '2210', 'Fabrikam Audio', 31900, 'USD', '5555555555554444', 'CAPTURED', now() - interval '3 days');
