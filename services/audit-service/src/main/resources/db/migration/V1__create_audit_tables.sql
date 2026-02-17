-- Audit Service Database Schema
-- Consolidation of V1, V2, and V3 into V1__create_audit_tables.sql

-- Enable uuid-ossp extension for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create trigger function for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create custom enum types
CREATE TYPE audit_action AS ENUM (
    'LOGIN', 'LOGOUT', 'REGISTER', 'PASSWORD_RESET', 'MFA_ENABLED', 'MFA_DISABLED',
    'ACCOUNT_LOCKED', 'ACCOUNT_UNLOCKED', 'PASSWORD_CHANGED',
    'PERMISSION_GRANTED', 'PERMISSION_REVOKED', 'ROLE_ASSIGNED', 'ROLE_REMOVED',
    'ACCESS_DENIED', 'UNAUTHORIZED_ACCESS_ATTEMPT',
    'RECORD_ACCESSED', 'RECORD_MODIFIED', 'RECORD_CREATED', 'RECORD_DELETED',
    'SENSITIVE_DATA_ACCESSED', 'PHI_ACCESSED',
    'WAF_BLOCKED_REQUEST', 'RATE_LIMIT_EXCEEDED', 'BRUTE_FORCE_DETECTED',
    'SUSPICIOUS_ACTIVITY_DETECTED', 'MALWARE_DETECTED',
    'ENCRYPTION_KEY_ROTATED', 'CERTIFICATE_EXPIRED',
    'CONFIGURATION_CHANGED', 'SECURITY_POLICY_UPDATED',
    'VULNERABILITY_SCANNED', 'SECURITY_PATCH_APPLIED',
    'DEVICE_REGISTERED', 'DEVICE_REVOKED', 'SESSION_STARTED', 'SESSION_EXPIRED',
    'CONCURRENT_SESSION_LIMIT_EXCEEDED',
    'USER_CREATED', 'USER_DELETED', 'USER_MODIFIED',
    'STAFF_ADDED', 'STAFF_REMOVED',
    'OWNERSHIP_TRANSFER_INITIATED', 'OWNERSHIP_TRANSFER_COMPLETED',
    'COMPLIANCE_CHECK_PASSED', 'COMPLIANCE_CHECK_FAILED',
    'AUDIT_LOG_ACCESSED', 'DATA_EXPORT_REQUESTED', 'DATA_EXPORT_COMPLETED',
    'ALERT_TRIGGERED', 'SECURITY_INCIDENT_REPORTED',
    'REPORT_GENERATED'
);

CREATE TYPE compliance_rule_type AS ENUM ('THRESHOLD', 'PATTERN', 'TIME_BASED', 'AGGREGATION');
CREATE TYPE compliance_severity AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
CREATE TYPE rule_status AS ENUM ('ACTIVE', 'INACTIVE', 'DRAFT');
CREATE TYPE compliance_framework AS ENUM ('HIPAA', 'GDPR', 'GHANA_DATA_PROTECTION_ACT', 'ISO_27001', 'NIST');
CREATE TYPE data_category AS ENUM ('AUDIT_EVENTS', 'COMPLIANCE_REPORTS', 'COMPLIANCE_VIOLATIONS', 'ARCHIVED_DATA');
CREATE TYPE retention_type AS ENUM ('DELETE', 'ARCHIVE');
CREATE TYPE report_status AS ENUM ('GENERATING', 'COMPLETED', 'FAILED');
CREATE TYPE report_format AS ENUM ('PDF', 'CSV', 'JSON', 'XML');

-- Create audit_events table (Consolidated with versioning fields from V2 and V3)
CREATE TABLE audit.audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(255) UNIQUE NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    user_id UUID,
    device_id UUID,
    hospital_id UUID,
    session_id VARCHAR(255),
    action audit_action NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(255),
    patient_id UUID,
    ip_address INET,
    user_agent TEXT,
    location TEXT,
    metadata TEXT,
    severity VARCHAR(20) DEFAULT 'INFO',
    compliance_flags TEXT,
    hash_value VARCHAR(64), -- SHA-256 hash for integrity
    previous_event_hash VARCHAR(64), -- Chain hash for immutability
    digital_signature TEXT, -- RSA digital signature for authenticity
    version INTEGER DEFAULT 0, -- From V2
    originating_device_id UUID, -- From V3
    doctor_user_id UUID, -- From V3
    version_number BIGINT, -- From V3
    parent_version_hash VARCHAR(255), -- From V3
    versioning_digital_signature TEXT, -- From V3
    conflict_resolution_metadata TEXT, -- From V3
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create compliance_rules table
CREATE TABLE audit.compliance_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    rule_type compliance_rule_type NOT NULL,
    conditions JSONB NOT NULL,
    actions JSONB NOT NULL,
    severity compliance_severity DEFAULT 'MEDIUM',
    status rule_status DEFAULT 'DRAFT',
    compliance_framework compliance_framework DEFAULT 'HIPAA',
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 1
);

-- Create retention_policies table
CREATE TABLE audit.retention_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    data_category data_category NOT NULL,
    retention_period INTERVAL NOT NULL,
    retention_type retention_type DEFAULT 'DELETE',
    archive_location VARCHAR(500),
    compression_enabled BOOLEAN DEFAULT true,
    encryption_enabled BOOLEAN DEFAULT true,
    compliance_framework VARCHAR(100),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create compliance_reports table
CREATE TABLE audit.compliance_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_type VARCHAR(100) NOT NULL,
    parameters JSONB,
    status report_status DEFAULT 'GENERATING',
    format report_format DEFAULT 'PDF',
    file_path VARCHAR(500),
    file_size BIGINT,
    record_count INTEGER,
    generated_by UUID NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create compliance_violations table
CREATE TABLE audit.compliance_violations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id UUID NOT NULL REFERENCES audit.compliance_rules(id),
    event_id UUID NOT NULL REFERENCES audit.audit_events(id),
    violation_type VARCHAR(100) NOT NULL,
    severity compliance_severity NOT NULL,
    description TEXT,
    details JSONB,
    resolved BOOLEAN DEFAULT false,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolved_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create data_export_jobs table
CREATE TABLE audit.data_export_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type VARCHAR(50) NOT NULL,
    parameters JSONB,
    status VARCHAR(20) DEFAULT 'PENDING',
    format report_format DEFAULT 'CSV',
    file_path VARCHAR(500),
    record_count INTEGER,
    file_size BIGINT,
    requested_by UUID NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_audit_events_timestamp ON audit.audit_events(timestamp);
CREATE INDEX idx_audit_events_user_id ON audit.audit_events(user_id);
CREATE INDEX idx_audit_events_patient_id ON audit.audit_events(patient_id);
CREATE INDEX idx_audit_events_action ON audit.audit_events(action);
CREATE INDEX idx_audit_events_resource ON audit.audit_events(resource_type, resource_id);
CREATE INDEX idx_audit_events_hash ON audit.audit_events(hash_value);
CREATE INDEX idx_audit_events_severity ON audit.audit_events(severity);
CREATE INDEX idx_audit_events_originating_device_id ON audit.audit_events(originating_device_id); -- From V3
CREATE INDEX idx_audit_events_doctor_user_id ON audit.audit_events(doctor_user_id); -- From V3
CREATE INDEX idx_audit_events_version_number ON audit.audit_events(version_number); -- From V3

CREATE INDEX idx_compliance_rules_status ON audit.compliance_rules(status);
CREATE INDEX idx_compliance_rules_type ON audit.compliance_rules(rule_type);

CREATE INDEX idx_compliance_violations_rule ON audit.compliance_violations(rule_id);
CREATE INDEX idx_compliance_violations_event ON audit.compliance_violations(event_id);
CREATE INDEX idx_compliance_violations_resolved ON audit.compliance_violations(resolved);

CREATE INDEX idx_data_export_jobs_status ON audit.data_export_jobs(status);
CREATE INDEX idx_data_export_jobs_requested_by ON audit.data_export_jobs(requested_by);

-- Triggers for updated_at
CREATE TRIGGER update_audit_events_updated_at
    BEFORE UPDATE ON audit.audit_events
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_compliance_rules_updated_at
    BEFORE UPDATE ON audit.compliance_rules
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_retention_policies_updated_at
    BEFORE UPDATE ON audit.retention_policies
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments
COMMENT ON TABLE audit.audit_events IS 'Primary audit event storage with integrity hashing and versioning';
COMMENT ON TABLE audit.compliance_rules IS 'Configurable compliance monitoring rules';
COMMENT ON TABLE audit.retention_policies IS 'Data retention and archival policies';
COMMENT ON TABLE audit.compliance_reports IS 'Generated compliance reports metadata';
COMMENT ON TABLE audit.compliance_violations IS 'Recorded compliance violations';
COMMENT ON TABLE audit.data_export_jobs IS 'Data export job tracking';

-- Insert default retention policies
INSERT INTO audit.retention_policies (name, description, data_category, retention_period, retention_type, compliance_framework, is_active) VALUES
('Audit Events Active', 'Active audit events retention for 7 years', 'AUDIT_EVENTS', '7 years', 'DELETE', 'HIPAA', true),
('Audit Events Archive', 'Archive older audit events to cold storage', 'AUDIT_EVENTS', '10 years', 'ARCHIVE', 'HIPAA', true),
('Compliance Reports', 'Compliance reports retention', 'COMPLIANCE_REPORTS', '10 years', 'DELETE', 'GDPR', true),
('Archived Data', 'Archived data retention', 'ARCHIVED_DATA', '20 years', 'DELETE', 'HIPAA', true);