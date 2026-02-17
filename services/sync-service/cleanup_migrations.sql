-- Cleanup script to reset migrations
-- Delete flyway history for V3 and V4
DELETE FROM sync.flyway_schema_history WHERE version IN ('3', '4');

-- Drop all objects created by V3 and V4
DROP VIEW IF EXISTS latest_medical_records;
DROP VIEW IF EXISTS medical_record_history;
DROP INDEX IF EXISTS idx_versions_conflict_status;
DROP INDEX IF EXISTS idx_versions_doctor_user;
DROP INDEX IF EXISTS idx_versions_change_type;
DROP INDEX IF EXISTS idx_versions_is_latest;
DROP INDEX IF EXISTS idx_versions_content_gin;
DROP INDEX IF EXISTS idx_versions_metadata_gin;
DROP INDEX IF EXISTS idx_versions_active_records;
DROP INDEX IF EXISTS idx_versions_created_at_user;
DROP INDEX IF EXISTS idx_versions_user_type_record_version;
DROP INDEX IF EXISTS idx_medical_record_versions_content_hash;
DROP INDEX IF EXISTS idx_medical_record_versions_parent;
DROP INDEX IF EXISTS idx_medical_record_versions_created_at;
DROP INDEX IF EXISTS idx_medical_record_versions_user_record;

DROP TRIGGER IF EXISTS prevent_deletes_trigger ON medical_record_versions;
DROP TRIGGER IF EXISTS prevent_updates_trigger ON medical_record_versions;
DROP TRIGGER IF EXISTS validate_version_chain_trigger ON medical_record_versions;
DROP TRIGGER IF EXISTS update_backup_preferences_updated_at ON user_backup_preferences;

DROP FUNCTION IF EXISTS prevent_version_deletes();
DROP FUNCTION IF EXISTS prevent_version_updates();
DROP FUNCTION IF EXISTS validate_version_chain();
DROP FUNCTION IF EXISTS create_yearly_partition(INTEGER);
DROP FUNCTION IF EXISTS detect_sync_conflicts(TEXT, UUID, JSONB);
DROP FUNCTION IF EXISTS validate_integrity_batch(INTEGER);
DROP FUNCTION IF EXISTS get_version_history_optimized(UUID, TEXT, UUID, INTEGER, INTEGER);
DROP FUNCTION IF EXISTS apply_retention_policy(INTEGER);
DROP FUNCTION IF EXISTS scheduled_retention_cleanup();

DROP TABLE IF EXISTS migration_status;
DROP TABLE IF EXISTS retention_audit_log;
DROP TABLE IF EXISTS record_latest;
DROP TABLE IF EXISTS medical_record_versions CASCADE;
DROP TABLE IF EXISTS user_backup_preferences;

DROP FUNCTION IF EXISTS update_updated_at_column();
