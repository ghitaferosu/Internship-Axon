--SECTION VERTICES: a drawn section's outline polygon, stored as ordered
--points. Deliberately a separate table, not columns on `sections` - this is
--a rendering-only concern (what buyers/admins see drawn), kept out of the
--core persistence model that seat/ticket code already depends on. No FK
--is declared back on `sections`; this table is the only side that knows
--about the relationship.
CREATE TABLE section_vertices(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    section_id BIGINT NOT NULL REFERENCES sections(id),
    vertex_order INTEGER NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    UNIQUE (section_id, vertex_order)
);

CREATE INDEX idx_section_vertices_section_id ON section_vertices(section_id);
