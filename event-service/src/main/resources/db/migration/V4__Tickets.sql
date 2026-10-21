--General admission sections have no seat rows, so they are not added here


CREATE TABLE tickets(
    id  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    seat_id BIGINT NOT NULL REFERENCES seats(id),
    status  VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'HELD', 'SOLD')),
    price_cents INTEGER NOT NULL CHECK (price_cents >= 0),
    owner_id  BIGINT,
    held_by BIGINT,
    held_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_tickets_event_seat UNIQUE (event_id, seat_id),
    CONSTRAINT chk_tickets_sold_has_owner CHECK (status <> 'SOLD' OR owner_id IS NOT NULL),
    CONSTRAINT chk_tickets_held_has_holder CHECK (status <> 'HELD' OR (held_by IS NOT NULL AND held_until IS NOT NULL))
);

CREATE INDEX idx_tickets_event_status ON tickets(event_id, status);
CREATE INDEX idx_tickets_owner_id ON tickets(owner_id);