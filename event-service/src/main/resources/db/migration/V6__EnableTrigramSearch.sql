CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_events_name_trgm ON events USING gin (name gin_trgm_ops);
CREATE INDEX idx_performers_name_trgm ON performers USING gin (name gin_trgm_ops);
CREATE INDEX idx_events_city_trgm ON events USING gin (city gin_trgm_ops);
CREATE INDEX idx_events_country_trgm ON events USING gin (country gin_trgm_ops);