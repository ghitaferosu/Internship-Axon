--Replace the fixed compass `direction` with a continuous `angle` (radians,
--from the stage origin (0,0) to the section's centroid) - the generalization
--that lets sections be arbitrary drawn polygons instead of 4 fixed rectangle
--orientations. See venue-authoring-rework in project memory.
ALTER TABLE sections DROP CONSTRAINT sections_direction_check;
ALTER TABLE sections DROP COLUMN direction;

ALTER TABLE sections ADD COLUMN angle DOUBLE PRECISION NOT NULL DEFAULT 0;
ALTER TABLE sections ALTER COLUMN angle DROP DEFAULT;
