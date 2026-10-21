ALTER TABLE payments DROP CONSTRAINT uq_payments_idempotency;
ALTER TABLE payments ADD CONSTRAINT uq_payments_payer_idempotency
    UNIQUE (payer_id, idempotency_key);