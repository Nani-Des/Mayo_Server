-- Hospital Integration Service Database Schema
-- Migration: V1__create_hospital_integration_tables.sql

-- Create schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS hospital_integration;

-- Set search path to the schema
SET search_path TO hospital_integration;

-- Hospitals table
CREATE TABLE hospitals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    postal_code VARCHAR(20),
    phone VARCHAR(50),
    email VARCHAR(255),
    website VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    integration_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    api_endpoint TEXT,
    api_key TEXT,
    supported_data_types TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

-- Hospital devices table
CREATE TABLE hospital_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id VARCHAR(100) UNIQUE NOT NULL,
    hospital_id UUID NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE,
    device_type VARCHAR(50) NOT NULL,
    device_name VARCHAR(255) NOT NULL,
    manufacturer VARCHAR(255),
    model VARCHAR(255),
    serial_number VARCHAR(255),
    firmware_version VARCHAR(100),
    ip_address VARCHAR(255),
    mac_address VARCHAR(255),
    connection_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'REGISTERED',
    supported_protocols TEXT,
    supported_data_formats TEXT,
    certificate TEXT,
    last_heartbeat TIMESTAMP WITH TIME ZONE,
    registered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

-- Activities table
CREATE TABLE activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id UUID NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE,
    device_id UUID REFERENCES hospital_devices(id) ON DELETE SET NULL,
    activity_type VARCHAR(50) NOT NULL,
    activity_description TEXT NOT NULL,
    user_id UUID,
    patient_id UUID,
    record_id VARCHAR(255),
    record_type VARCHAR(50),
    ip_address VARCHAR(255),
    user_agent TEXT,
    metadata TEXT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Data transfer sessions table
CREATE TABLE data_transfer_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(50) UNIQUE NOT NULL,
    hospital_id UUID NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES hospital_devices(id) ON DELETE CASCADE,
    transfer_type VARCHAR(20) NOT NULL,
    data_type VARCHAR(50) NOT NULL,
    protocol VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'INITIATED',
    total_records BIGINT,
    processed_records BIGINT DEFAULT 0,
    failed_records BIGINT DEFAULT 0,
    data_size_bytes BIGINT,
    transferred_bytes BIGINT DEFAULT 0,
    source_endpoint TEXT,
    destination_endpoint TEXT,
    initiated_by UUID,
    error_message TEXT,
    metadata TEXT,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

-- Indexes for performance
CREATE INDEX idx_hospital_devices_hospital_id ON hospital_devices(hospital_id);
CREATE INDEX idx_hospital_devices_status ON hospital_devices(status);
CREATE INDEX idx_hospital_devices_device_type ON hospital_devices(device_type);

CREATE INDEX idx_activities_hospital_device ON activities(hospital_id, device_id);
CREATE INDEX idx_activities_type_timestamp ON activities(activity_type, timestamp);
CREATE INDEX idx_activities_timestamp ON activities(timestamp);
CREATE INDEX idx_activities_user_id ON activities(user_id);
CREATE INDEX idx_activities_patient_id ON activities(patient_id);

CREATE INDEX idx_transfer_sessions_hospital_device ON data_transfer_sessions(hospital_id, device_id);
CREATE INDEX idx_transfer_sessions_status_timestamp ON data_transfer_sessions(status, created_at);
CREATE INDEX idx_transfer_sessions_session_status ON data_transfer_sessions(session_id, status);

-- Comments for documentation
COMMENT ON TABLE hospitals IS 'Hospital information and integration settings';
COMMENT ON TABLE hospital_devices IS 'Registered hospital devices for data integration';
COMMENT ON TABLE activities IS 'Activity log for hospital device interactions and data access';
COMMENT ON TABLE data_transfer_sessions IS 'Data transfer session tracking and monitoring';

COMMENT ON COLUMN hospitals.integration_enabled IS 'Whether this hospital is enabled for data integration';
COMMENT ON COLUMN hospitals.supported_data_types IS 'JSON array of supported data types for this hospital';
COMMENT ON COLUMN hospital_devices.supported_protocols IS 'JSON array of supported communication protocols';
COMMENT ON COLUMN hospital_devices.supported_data_formats IS 'JSON array of supported data formats';
COMMENT ON COLUMN activities.metadata IS 'Additional JSON metadata for the activity';
COMMENT ON COLUMN data_transfer_sessions.metadata IS 'Additional JSON metadata for the transfer session';