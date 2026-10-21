--VENUES
CREATE TABLE venues(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    no_seats INTEGER NOT NULL,
    CONSTRAINT uq_venues_name_address UNIQUE (name,address)
);

--SEATS
CREATE TABLE seats(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_id BIGINT NOT NULL REFERENCES venues(id),
    section VARCHAR(50) NOT NULL,
    row_number INTEGER NOT NULL ,
    seat_number INTEGER NOT NULL,
    CONSTRAINT uq_seats_venue_section_row_seat UNIQUE (venue_id,section,row_number,seat_number)
);

CREATE INDEX idx_seats_venue_id ON seats(venue_id);

--PERFORMERS
CREATE TABLE performers(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL ,
    genre VARCHAR(255) NOT NULL,
    description TEXT
);

--EVENTS
CREATE TABLE events(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY ,
    venue_id BIGINT NOT NULL REFERENCES venues(id),
    name VARCHAR(255) NOT NULL ,
    country VARCHAR(255) NOT NULL ,
    city VARCHAR(255) NOT NULL ,
    event_start_hour TIMESTAMPTZ NOT NULL ,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                   CHECK (status IN ('DRAFT','SETTING_UP','LIVE'))
);

CREATE INDEX idx_events_venue_id ON events(venue_id);
CREATE INDEX idx_events_country ON events(country);
CREATE INDEX idx_events_city ON events(city);
CREATE INDEX idx_events_start_hour ON events(event_start_hour);


--EVENT_PERFORMER
CREATE TABLE events_performers(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY ,
    event_id BIGINT NOT NULL REFERENCES events(id),
    performer_id BIGINT NOT NULL REFERENCES performers(id),
    start_hour TIMESTAMPTZ NOT NULL,
    end_hour TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_events_performers_event_performer_start
                              UNIQUE (event_id,performer_id,start_hour),
    CONSTRAINT chk_events_performers_hours CHECK ( start_hour < end_hour )
);

CREATE INDEX idx_events_performers_event_id ON events_performers(event_id);
CREATE INDEX idx_events_performers_performer_id ON events_performers(performer_id);



