-- Sync Service Database Migration
-- V1: Complete consolidated schema

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Update trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- Sync Sessions table
-- ============================================================
CREATE TABLE sync_sessions (
    id UUID PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    session_start TIMESTAMP NOT NULL,
    session_end TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    last_sync_timestamp TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version INTEGER NOT NULL
);

CREATE INDEX idx_sync_sessions_user_device ON sync_sessions(user_id, device_id);
CREATE INDEX idx_sync_sessions_status ON sync_sessions(status);

-- ============================================================
-- Devices table (with hospital_id from V6)
-- ============================================================
CREATE TABLE devices (
    id UUID PRIMARY KEY,
    device_id VARCHAR(255) UNIQUE NOT NULL,
    device_type VARCHAR(20) NOT NULL,
    pairing_method VARCHAR(20) NOT NULL,
    user_id UUID NOT NULL,
    public_key TEXT,
    certificate TEXT,
    status VARCHAR(20) NOT NULL,
    last_seen TIMESTAMP,
    paired_at TIMESTAMP NOT NULL,
    hospital_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version INTEGER NOT NULL
);

CREATE INDEX idx_devices_user_id ON devices(user_id);
CREATE INDEX idx_devices_device_id ON devices(device_id);
CREATE INDEX idx_devices_status ON devices(status);
CREATE INDEX idx_devices_hospital_id ON devices(hospital_id);

-- ============================================================
-- Conflicts table
-- ============================================================
CREATE TABLE conflicts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    record_id VARCHAR(255) NOT NULL,
    record_type VARCHAR(100) NOT NULL,
    local_version BIGINT NOT NULL,
    server_version BIGINT NOT NULL,
    conflict_type VARCHAR(30) NOT NULL,
    resolution_status VARCHAR(30) NOT NULL,
    local_data TEXT,
    server_data TEXT,
    resolved_data TEXT,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version INTEGER NOT NULL
);

CREATE INDEX idx_conflicts_user_id ON conflicts(user_id);
CREATE INDEX idx_conflicts_resolution_status ON conflicts(resolution_status);
CREATE INDEX idx_conflicts_record_id ON conflicts(record_id);

-- ============================================================
-- Delta Changes table (with CRDT columns from V2)
-- ============================================================
CREATE TABLE delta_changes (
    id UUID PRIMARY KEY,
    record_id VARCHAR(255) NOT NULL,
    record_type VARCHAR(100) NOT NULL,
    change_type VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    user_id UUID NOT NULL,
    device_id VARCHAR(255),
    data TEXT,
    document_id VARCHAR(255),
    crdt_state BYTEA,
    is_crdt_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    entity_version INTEGER NOT NULL
);

CREATE INDEX idx_delta_changes_user_version ON delta_changes(user_id, version);
CREATE INDEX idx_delta_changes_record_type ON delta_changes(record_type);
CREATE INDEX idx_delta_changes_timestamp ON delta_changes(timestamp);
CREATE INDEX idx_delta_changes_document_id ON delta_changes(document_id);
CREATE INDEX idx_delta_changes_crdt_enabled ON delta_changes(is_crdt_enabled);

-- ============================================================
-- CRDT Documents table (from V2)
-- ============================================================
CREATE TABLE crdt_documents (
    id UUID PRIMARY KEY,
    document_id VARCHAR(255) NOT NULL UNIQUE,
    document_type VARCHAR(100) NOT NULL,
    current_state BYTEA NOT NULL,
    last_updated TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version INTEGER NOT NULL
);

CREATE INDEX idx_crdt_documents_document_type ON crdt_documents(document_type);
CREATE INDEX idx_crdt_documents_last_updated ON crdt_documents(last_updated);

-- ============================================================
-- Medical Record Versions table (from V3, with V4 column mods applied)
-- Final column types already applied — no ALTER needed
-- ============================================================
CREATE TABLE medical_record_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    record_type VARCHAR(50) NOT NULL,
    record_id VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL,
    parent_version_id UUID REFERENCES medical_record_versions(id),
    content JSONB NOT NULL,
    metadata JSONB,
    originating_device_id TEXT NOT NULL,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cryptographic_signature TEXT,
    content_hash TEXT NOT NULL,
    is_latest BOOLEAN NOT NULL DEFAULT false,
    change_type VARCHAR(50) NOT NULL,
    doctor_user_id UUID,
    conflict_resolution_status VARCHAR(50),
    chain_depth INTEGER,
    parent_hash TEXT,
    is_valid BOOLEAN,
    last_verified TIMESTAMP,
    UNIQUE(record_type, record_id, version)
);

CREATE INDEX idx_medical_record_versions_user_record ON medical_record_versions(user_id, record_type, record_id);
CREATE INDEX idx_medical_record_versions_created_at ON medical_record_versions(created_at);
CREATE INDEX idx_medical_record_versions_parent ON medical_record_versions(parent_version_id);
CREATE INDEX idx_medical_record_versions_content_hash ON medical_record_versions(content_hash);
CREATE INDEX idx_versions_user_type_record_version ON medical_record_versions(user_id, record_type, record_id, version DESC);
CREATE INDEX idx_versions_created_at_user ON medical_record_versions(created_at DESC, user_id);
CREATE INDEX idx_versions_active_records ON medical_record_versions(record_type, record_id, version DESC);
CREATE INDEX idx_versions_metadata_gin ON medical_record_versions USING GIN (metadata);
CREATE INDEX idx_versions_content_gin ON medical_record_versions USING GIN (content);
CREATE INDEX idx_versions_is_latest ON medical_record_versions(record_type, record_id, is_latest) WHERE is_latest = true;
CREATE INDEX idx_versions_change_type ON medical_record_versions(change_type);
CREATE INDEX idx_versions_doctor_user ON medical_record_versions(doctor_user_id) WHERE doctor_user_id IS NOT NULL;
CREATE INDEX idx_versions_conflict_status ON medical_record_versions(conflict_resolution_status) WHERE conflict_resolution_status IS NOT NULL;

-- ============================================================
-- User Backup Preferences table (from V3, with V4 PK change applied)
-- ============================================================
CREATE TABLE user_backup_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    backup_enabled BOOLEAN NOT NULL DEFAULT false,
    encryption_key_hash TEXT,
    last_backup_at TIMESTAMP,
    backup_frequency TEXT CHECK (backup_frequency IN ('manual', 'daily', 'weekly', 'monthly')),
    retention_years INTEGER DEFAULT 7,
    consent_given_at TIMESTAMP,
    consent_version TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER update_backup_preferences_updated_at
    BEFORE UPDATE ON user_backup_preferences
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- Record Latest table (version cache)
-- ============================================================
CREATE TABLE record_latest (
    record_type VARCHAR(50) NOT NULL,
    record_id UUID NOT NULL,
    latest_version_id UUID NOT NULL REFERENCES medical_record_versions(id),
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (record_type, record_id)
);

-- ============================================================
-- Retention Audit Log table
-- ============================================================
CREATE TABLE retention_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action_type TEXT NOT NULL,
    record_type TEXT,
    record_id UUID,
    user_id UUID,
    performed_by UUID NOT NULL,
    performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason TEXT,
    compliance_reference TEXT,
    details JSONB
);

-- ============================================================
-- Migration Status table
-- ============================================================
CREATE TABLE migration_status (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    table_name TEXT NOT NULL,
    record_id UUID NOT NULL,
    migration_status TEXT NOT NULL DEFAULT 'pending',
    migrated_at TIMESTAMP,
    error_message TEXT,
    UNIQUE(table_name, record_id)
);

-- ============================================================
-- Functions for versioning integrity
-- ============================================================

-- Validate version chain integrity
CREATE OR REPLACE FUNCTION validate_version_chain()
RETURNS TRIGGER AS $$
DECLARE
    expected_version INTEGER;
    parent_exists BOOLEAN;
BEGIN
    IF NEW.version > 1 THEN
        SELECT EXISTS(
            SELECT 1 FROM medical_record_versions
            WHERE record_type = NEW.record_type
            AND record_id = NEW.record_id
            AND version = NEW.version - 1
        ) INTO parent_exists;

        IF NOT parent_exists THEN
            RAISE EXCEPTION 'Parent version % does not exist for record %', NEW.version - 1, NEW.record_id;
        END IF;
    END IF;

    SELECT COALESCE(MAX(version), 0) + 1 INTO expected_version
    FROM medical_record_versions
    WHERE record_type = NEW.record_type AND record_id = NEW.record_id;

    IF NEW.version != expected_version THEN
        RAISE EXCEPTION 'Version number must be sequential. Expected: %, Got: %', expected_version, NEW.version;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_version_chain_trigger
    BEFORE INSERT ON medical_record_versions
    FOR EACH ROW EXECUTE FUNCTION validate_version_chain();

-- Prevent updates to version records (immutability)
CREATE OR REPLACE FUNCTION prevent_version_updates()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Version records are immutable and cannot be modified';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION prevent_version_deletes()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Version records are immutable and cannot be deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER prevent_updates_trigger
    BEFORE UPDATE ON medical_record_versions
    FOR EACH ROW EXECUTE FUNCTION prevent_version_updates();

CREATE TRIGGER prevent_deletes_trigger
    BEFORE DELETE ON medical_record_versions
    FOR EACH ROW EXECUTE FUNCTION prevent_version_deletes();

-- ============================================================
-- Sync conflict detection
-- ============================================================
CREATE OR REPLACE FUNCTION detect_sync_conflicts(
    p_record_type TEXT,
    p_record_id UUID,
    p_incoming_versions JSONB
) RETURNS TABLE (
    conflict_type TEXT,
    local_version INTEGER,
    incoming_version INTEGER,
    resolution_strategy TEXT
) AS $$
DECLARE
    local_latest INTEGER;
    incoming_latest INTEGER;
BEGIN
    SELECT MAX(version) INTO local_latest
    FROM medical_record_versions
    WHERE record_type = p_record_type AND record_id = p_record_id::VARCHAR;

    SELECT (p_incoming_versions->>'version')::INTEGER INTO incoming_latest;

    IF local_latest IS NULL THEN
        RETURN QUERY SELECT 'new_record'::TEXT, 0, incoming_latest, 'accept_all'::TEXT;
    ELSIF incoming_latest > local_latest THEN
        RETURN QUERY SELECT 'newer_versions'::TEXT, local_latest, incoming_latest, 'append_chain'::TEXT;
    ELSIF incoming_latest = local_latest THEN
        RETURN QUERY SELECT 'duplicate_versions'::TEXT, local_latest, incoming_latest, 'verify_integrity'::TEXT;
    ELSE
        RETURN QUERY SELECT 'stale_versions'::TEXT, local_latest, incoming_latest, 'reject_sync'::TEXT;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Batch integrity validation
CREATE OR REPLACE FUNCTION validate_integrity_batch(
    p_limit INTEGER DEFAULT 1000
) RETURNS TABLE (
    record_type TEXT,
    record_id UUID,
    issues_found INTEGER,
    last_checked TIMESTAMP
) AS $$
DECLARE
    rec RECORD;
    issue_count INTEGER;
BEGIN
    FOR rec IN
        SELECT DISTINCT mrv.record_type, mrv.record_id::UUID
        FROM medical_record_versions mrv
        ORDER BY mrv.record_type, mrv.record_id
        LIMIT p_limit
    LOOP
        SELECT COUNT(*) INTO issue_count
        FROM medical_record_versions v1
        LEFT JOIN medical_record_versions v2 ON v1.record_type = v2.record_type
            AND v1.record_id = v2.record_id
            AND v1.version = v2.version - 1
        WHERE v1.record_type = rec.record_type
            AND v1.record_id = rec.record_id::VARCHAR
            AND v2.id IS NULL
            AND v1.version > 1;

        RETURN QUERY SELECT
            rec.record_type,
            rec.record_id,
            issue_count,
            CURRENT_TIMESTAMP;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Optimized version history query
CREATE OR REPLACE FUNCTION get_version_history_optimized(
    p_user_id UUID,
    p_record_type TEXT,
    p_record_id UUID,
    p_limit INTEGER DEFAULT 50,
    p_offset INTEGER DEFAULT 0
)
RETURNS TABLE (
    version INTEGER,
    created_at TIMESTAMP,
    updated_by TEXT,
    change_summary TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        v.version::INTEGER,
        v.created_at,
        v.updated_by::TEXT,
        v.metadata->>'changeType' as change_summary
    FROM medical_record_versions v
    WHERE v.user_id = p_user_id
      AND v.record_type = p_record_type
      AND v.record_id = p_record_id::VARCHAR
    ORDER BY v.version DESC
    LIMIT p_limit OFFSET p_offset;
END;
$$ LANGUAGE plpgsql;

-- Yearly partition creation function
CREATE OR REPLACE FUNCTION create_yearly_partition(
    p_year INTEGER
) RETURNS VOID AS $$
DECLARE
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
BEGIN
    partition_name := 'medical_record_versions_y' || p_year;
    start_date := make_date(p_year, 1, 1);
    end_date := make_date(p_year + 1, 1, 1);

    EXECUTE format('CREATE TABLE %I PARTITION OF medical_record_versions FOR VALUES FROM (%L) TO (%L)',
                   partition_name, start_date, end_date);
END;
$$ LANGUAGE plpgsql;

-- Retention policy function
CREATE OR REPLACE FUNCTION apply_retention_policy(
    p_retention_years INTEGER DEFAULT 7
) RETURNS TABLE (
    records_archived INTEGER,
    records_deleted INTEGER
) AS $$
DECLARE
    cutoff_date TIMESTAMP;
    archived_count INTEGER := 0;
    deleted_count INTEGER := 0;
BEGIN
    cutoff_date := CURRENT_TIMESTAMP - INTERVAL '1 year' * p_retention_years;

    SELECT COUNT(*) INTO archived_count
    FROM medical_record_versions
    WHERE created_at < cutoff_date;

    RETURN QUERY SELECT archived_count, deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Scheduled retention cleanup
CREATE OR REPLACE FUNCTION scheduled_retention_cleanup() RETURNS VOID AS $$
DECLARE
    result RECORD;
BEGIN
    FOR result IN SELECT * FROM apply_retention_policy(7) LOOP
        INSERT INTO retention_audit_log (
            action_type, performed_by, reason, details
        ) VALUES (
            'RETENTION_APPLIED',
            '00000000-0000-0000-0000-000000000000'::UUID,
            'Automated 7-year retention policy',
            jsonb_build_object(
                'archived_records', result.records_archived,
                'deleted_records', result.records_deleted,
                'retention_years', 7
            )
        );
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- Views
-- ============================================================
CREATE VIEW latest_medical_records AS
SELECT DISTINCT ON (record_type, record_id)
    id, user_id, record_type, record_id, version, content, metadata,
    originating_device_id, updated_by, created_at, cryptographic_signature
FROM medical_record_versions
ORDER BY record_type, record_id, version DESC;

CREATE VIEW medical_record_history AS
SELECT
    v.*,
    ROW_NUMBER() OVER (PARTITION BY record_type, record_id ORDER BY version) as chain_position
FROM medical_record_versions v;

-- ============================================================
-- Comments
-- ============================================================
COMMENT ON TABLE sync_sessions IS 'Active sync sessions between devices and server';
COMMENT ON TABLE devices IS 'Registered devices for offline sync';
COMMENT ON TABLE conflicts IS 'Sync conflicts requiring resolution';
COMMENT ON TABLE delta_changes IS 'Incremental data changes for sync';
COMMENT ON TABLE crdt_documents IS 'CRDT document states for conflict-free replication';
COMMENT ON TABLE medical_record_versions IS 'Immutable chain of medical record versions';
COMMENT ON TABLE user_backup_preferences IS 'User preferences for backup and retention';