-- Update Flyway schema history to accept the current migrations
-- This fixes the checksum mismatches for V3 and V4

-- Get the current checksums from the local files
-- V3 checksum: 1560547164
-- V4 checksum: -1640668627

UPDATE sync.flyway_schema_history 
SET checksum = 1560547164 
WHERE version = '3';

UPDATE sync.flyway_schema_history 
SET checksum = -1640668627 
WHERE version = '4';

-- If V4 doesn't exist yet (migration was skipped), we need to manually run the V4 script
-- Check if V4 was applied:
-- SELECT * FROM sync.flyway_schema_history WHERE version = '4';
