-- Mayo EMR Database Initialization Script
-- This script runs automatically when PostgreSQL starts for the first time

-- Enable necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Create schemas for each service
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS patient_record;
CREATE SCHEMA IF NOT EXISTS sync;
CREATE SCHEMA IF NOT EXISTS device_registry;
CREATE SCHEMA IF NOT EXISTS hospital_integration;
CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS appointment;
CREATE SCHEMA IF NOT EXISTS notification;

-- Grant permissions to the mayo user
GRANT ALL PRIVILEGES ON SCHEMA auth TO mayo;
GRANT ALL PRIVILEGES ON SCHEMA patient_record TO mayo;
GRANT ALL PRIVILEGES ON SCHEMA sync TO mayo;
GRANT ALL PRIVILEGES ON SCHEMA device_registry TO mayo;
GRANT ALL PRIVILEGES ON SCHEMA hospital_integration TO mayo;
GRANT ALL PRIVILEGES ON SCHEMA audit TO mayo;
GRANT ALL PRIVILEGES ON SCHEMA appointment TO mayo;
GRANT ALL PRIVILEGES ON SCHEMA notification TO mayo;

-- Create shared functions
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Helper to create updated_at triggers easily
CREATE OR REPLACE FUNCTION create_updated_at_trigger(table_name text, schema_name text)
RETURNS void AS $$
BEGIN
    EXECUTE format('
        CREATE TRIGGER update_%I_updated_at
        BEFORE UPDATE ON %I.%I
        FOR EACH ROW
        EXECUTE FUNCTION update_updated_at_column();
    ', table_name, schema_name, table_name);
END;
$$ LANGUAGE plpgsql;

COMMENT ON DATABASE mayo_db IS 'Mayo EMR Database';
COMMENT ON SCHEMA auth IS 'Authentication and RBAC';
COMMENT ON SCHEMA patient_record IS 'Patient records and demographics';
COMMENT ON SCHEMA sync IS 'Offline synchronization and versioning';
COMMENT ON SCHEMA device_registry IS 'Device registration and heartbeats';
COMMENT ON SCHEMA hospital_integration IS 'External hospital system integration';
COMMENT ON SCHEMA audit IS 'Audit logs and compliance';
COMMENT ON SCHEMA appointment IS 'Appointment scheduling';
COMMENT ON SCHEMA notification IS 'System and patient notifications';
