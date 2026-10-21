--Drop ARC/ANGLED_GRID support: sections are now GRID or GENERAL_ADMISSION only.
--The arc/rotation rendering columns are replaced by a plain compass direction;
--at most one GRID section per direction is enforced at the application level
--(the Validator), not here - levels (multiple sections per side) aren't
--supported yet.
ALTER TABLE sections DROP CONSTRAINT sections_shape_check;
ALTER TABLE sections DROP COLUMN curve_step;
ALTER TABLE sections DROP COLUMN start_angle;
ALTER TABLE sections DROP COLUMN end_angle;
ALTER TABLE sections DROP COLUMN inner_radius;
ALTER TABLE sections DROP COLUMN rotate_angle;

ALTER TABLE sections ADD COLUMN direction VARCHAR(10)
                  CHECK (direction IN ('NORTH','SOUTH','EAST','WEST'));

ALTER TABLE sections ADD CONSTRAINT chk_sections_shape
                  CHECK (shape IN ('GRID','GENERAL_ADMISSION'));
