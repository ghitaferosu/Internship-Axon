CREATE TABLE payments(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES tickets(id),
    payer_id BIGINT NOT NULL,
    amount_cents INTEGER NOT NULL CHECK (amount_cents >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUCCEEDED','FAILED')),
    idempotency_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_payments_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_payments_ticket_id ON payments(ticket_id);