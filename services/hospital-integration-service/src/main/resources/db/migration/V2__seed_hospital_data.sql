-- Seed Hospital Data for Device Registration
-- Migration: V2__seed_hospital_data.sql

-- Set search path to the schema
SET search_path TO hospital_integration;

-- Insert default hospital (matching the UUID used in device registration requests)
INSERT INTO hospitals (id, hospital_id, name, address, city, state, country, phone, email, status, integration_enabled, supported_data_types)
VALUES (
    '550e8400-e29b-41d4-a716-446655440000',  -- This UUID matches the hospitalId in device registration requests
    'HOSP-001',
    'Mayo Clinic',
    '200 First Street SW',
    'Rochester',
    'Minnesota',
    'United States',
    '+1-507-284-2511',
    'info@mayoclinic.org',
    'ACTIVE',
    TRUE,
    '["patient_records", "lab_results", "medications", "imaging", "appointments"]'
);

-- Insert a secondary hospital for testing
INSERT INTO hospitals (id, hospital_id, name, address, city, state, country, phone, email, status, integration_enabled, supported_data_types)
VALUES (
    '660e8400-e29b-41d4-a716-446655440001',  -- Secondary test hospital
    'HOSP-002',
    'Mayo Clinic Hospital',
    '5777 East Mayo Boulevard',
    'Phoenix',
    'Arizona',
    'United States',
    '+1-480-342-2000',
    'phoenix.info@mayoclinic.org',
    'ACTIVE',
    TRUE,
    '["patient_records", "lab_results", "medications"]'
);
