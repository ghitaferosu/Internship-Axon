--SECTIONS
CREATE TABLE sections(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_id BIGINT NOT NULL REFERENCES venues(id),
    section_name VARCHAR(255) NOT NULL,
    shape VARCHAR(20) NOT NULL
                  CHECK (shape IN ('ARC','GRID','ANGLED_GRID','GENERAL_ADMISSION')),
    row_start INTEGER NOT NULL,
    row_end INTEGER NOT NULL,
    seat_start INTEGER NOT NULL,
    seat_end INTEGER NOT NULL,
    curve_step INTEGER NOT NULL,
    start_angle INTEGER NOT NULL,
    end_angle INTEGER NOT NULL,
    inner_radius INTEGER NOT NULL,
    row_spacing INTEGER NOT NULL,
    origin_x INTEGER NOT NULL,
    origin_y INTEGER NOT NULL,
    col_spacing INTEGER NOT NULL,
    rotate_angle INTEGER NOT NULL,
    capacity INTEGER NOT NULL
);

CREATE INDEX idx_sections_venue_id ON sections(venue_id);

--SEATS: re-key from (venue_id, free-text section) to a proper FK on sections.
--GENERAL_ADMISSION sections never get Seat rows at all, so section_id is never
--null for a row that exists - it's simply absent for those sections.
ALTER TABLE seats DROP CONSTRAINT uq_seats_venue_section_row_seat;
DROP INDEX idx_seats_venue_id;
ALTER TABLE seats DROP COLUMN venue_id;
ALTER TABLE seats DROP COLUMN section;

ALTER TABLE seats ADD COLUMN section_id BIGINT NOT NULL REFERENCES sections(id);
ALTER TABLE seats ADD CONSTRAINT uq_seats_section_row_seat UNIQUE (section_id,row_number,seat_number);

CREATE INDEX idx_seats_section_id ON seats(section_id);
