-- Auth Service Seed Data
-- V2: Test users for development environment
-- Password for all test users: password123
-- BCrypt hash generated with BCryptPasswordEncoder strength 10

-- Default Hospital (matches hospital-integration-service seed)
INSERT INTO auth.hospitals (id, hospital_id, name, address, city, state, country, phone, email, status, integration_enabled)
VALUES (
    '550e8400-e29b-41d4-a716-446655440000',
    'HOSP-001',
    'Mayo Clinic',
    '200 First Street SW',
    'Rochester',
    'Minnesota',
    'United States',
    '+1-507-284-2511',
    'info@mayoclinic.org',
    'ACTIVE',
    TRUE
);

-- Super Admin User (system-wide access, no hospital assignment)
INSERT INTO auth.users (id, email, password, ghana_card_id, full_name, phone_number, user_type, is_super_admin, email_verified, is_active)
VALUES (
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    'superadmin@mayo.com',
    '$2a$10$YQIuAqrsUitPQWbks9iyj.F8FM7hhcR12Jk.Sb9nZLe2yyvF67cx2',
    'GHA-SADMIN-001',
    'Super Administrator',
    '+233501234567',
    'SUPER_ADMIN',
    TRUE,
    TRUE,
    TRUE
);

-- Hospital Admin User (hospital-level access)
INSERT INTO auth.users (id, email, password, ghana_card_id, full_name, phone_number, user_type, hospital_id, email_verified, is_active)
VALUES (
    'a2b3c4d5-e6f7-8901-abcd-ef2345678901',
    'hospitaladmin@mayo.com',
    '$2a$10$YQIuAqrsUitPQWbks9iyj.F8FM7hhcR12Jk.Sb9nZLe2yyvF67cx2',
    'GHA-HADMIN-001',
    'Hospital Administrator',
    '+233501234580',
    'HOSPITAL_ADMIN',
    '550e8400-e29b-41d4-a716-446655440000',
    TRUE,
    TRUE
);

-- Doctor User
INSERT INTO auth.users (id, email, password, ghana_card_id, full_name, phone_number, user_type, hospital_id, email_verified, is_active)
VALUES (
    'b2c3d4e5-f6a7-8901-bcde-f12345678901',
    'doctor@mayo.com',
    '$2a$10$YQIuAqrsUitPQWbks9iyj.F8FM7hhcR12Jk.Sb9nZLe2yyvF67cx2',
    'GHA-DOCTOR-001',
    'Dr. John Smith',
    '+233501234568',
    'DOCTOR',
    '550e8400-e29b-41d4-a716-446655440000',
    TRUE,
    TRUE
);

-- Nurse User
INSERT INTO auth.users (id, email, password, ghana_card_id, full_name, phone_number, user_type, hospital_id, email_verified, is_active)
VALUES (
    'c3d4e5f6-a7b8-9012-cdef-123456789012',
    'nurse@mayo.com',
    '$2a$10$YQIuAqrsUitPQWbks9iyj.F8FM7hhcR12Jk.Sb9nZLe2yyvF67cx2',
    'GHA-NURSE-001',
    'Nurse Jane Doe',
    '+233501234569',
    'NURSE',
    '550e8400-e29b-41d4-a716-446655440000',
    TRUE,
    TRUE
);

-- Receptionist User
INSERT INTO auth.users (id, email, password, ghana_card_id, full_name, phone_number, user_type, hospital_id, email_verified, is_active)
VALUES (
    'd4e5f6a7-b8c9-0123-def0-123456789abc',
    'receptionist@mayo.com',
    '$2a$10$YQIuAqrsUitPQWbks9iyj.F8FM7hhcR12Jk.Sb9nZLe2yyvF67cx2',
    'GHA-RECEP-001',
    'Reception Desk',
    '+233501234570',
    'RECEPTIONIST',
    '550e8400-e29b-41d4-a716-446655440000',
    TRUE,
    TRUE
);

-- Patient User
INSERT INTO auth.users (id, email, password, ghana_card_id, full_name, phone_number, user_type, email_verified, is_active)
VALUES (
    'e5f6a7b8-c9d0-1234-ef01-23456789abcd',
    'patient@mayo.com',
    '$2a$10$YQIuAqrsUitPQWbks9iyj.F8FM7hhcR12Jk.Sb9nZLe2yyvF67cx2',
    'GHA-PATIENT-001',
    'John Patient',
    '+233501234571',
    'PATIENT',
    TRUE,
    TRUE
);

-- Test Patient User
INSERT INTO auth.users (id, email, password, ghana_card_id, full_name, phone_number, user_type, email_verified, is_active)
VALUES (
    'f6a7b8c9-d0e1-2345-f012-3456789abcde',
    'test@mayo.com',
    '$2a$10$YQIuAqrsUitPQWbks9iyj.F8FM7hhcR12Jk.Sb9nZLe2yyvF67cx2',
    'GHA-TEST-001',
    'Test User',
    '+233501234572',
    'PATIENT',
    TRUE,
    TRUE
);
