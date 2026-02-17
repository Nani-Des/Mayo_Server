-- Auth Service Database Migration
-- V1: Complete consolidated schema for auth service

-- Create update trigger function
CREATE OR REPLACE FUNCTION auth.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create enum types (NO legacy ADMIN — use SUPER_ADMIN / HOSPITAL_ADMIN)
CREATE TYPE auth.device_status AS ENUM ('ACTIVE', 'INACTIVE', 'REVOKED', 'EXPIRED');
CREATE TYPE auth.device_type AS ENUM ('MOBILE', 'DESKTOP', 'TABLET', 'IOT', 'WEARABLE');
CREATE TYPE auth.user_type AS ENUM ('PATIENT', 'DOCTOR', 'NURSE', 'SUPER_ADMIN', 'HOSPITAL_ADMIN', 'RECEPTIONIST');

-- ============================================================
-- Hospitals table (source of truth for hospital entities)
-- ============================================================
CREATE TABLE auth.hospitals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hospital_id VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    phone VARCHAR(50),
    email VARCHAR(255),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    integration_enabled BOOLEAN DEFAULT FALSE,
    supported_data_types TEXT[],
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_hospitals_hospital_id ON auth.hospitals(hospital_id);
CREATE INDEX idx_hospitals_status ON auth.hospitals(status);

-- ============================================================
-- Users table
-- ============================================================
CREATE TABLE auth.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    ghana_card_id VARCHAR(255) UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20),
    user_type VARCHAR(255) NOT NULL,
    device_type VARCHAR(255),
    supabase_user_id UUID,
    hospital_id UUID,
    is_super_admin BOOLEAN NOT NULL DEFAULT FALSE,
    permissions JSONB DEFAULT '[]'::jsonb,
    role_id UUID,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON auth.users(email);
CREATE INDEX idx_users_ghana_card ON auth.users(ghana_card_id);
CREATE INDEX idx_users_user_type ON auth.users(user_type);
CREATE INDEX idx_users_is_super_admin ON auth.users(is_super_admin);
CREATE INDEX idx_users_permissions ON auth.users USING gin(permissions);

-- ============================================================
-- Roles table (RBAC)
-- ============================================================
CREATE TABLE auth.roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    permissions JSONB DEFAULT '[]'::jsonb,
    user_types VARCHAR(255)[] DEFAULT '{}',
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

-- User-Role mapping (many-to-many)
CREATE TABLE auth.user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    hospital_id UUID,
    granted_by UUID,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    CONSTRAINT user_roles_user_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE,
    CONSTRAINT user_roles_role_fkey FOREIGN KEY (role_id) REFERENCES auth.roles(id) ON DELETE CASCADE,
    CONSTRAINT user_roles_user_role_hospital_unique UNIQUE (user_id, role_id, hospital_id)
);

CREATE INDEX idx_user_roles_user_id ON auth.user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON auth.user_roles(role_id);
CREATE INDEX idx_user_roles_hospital_id ON auth.user_roles(hospital_id);

-- Add FK from users.role_id → roles.id
ALTER TABLE auth.users
    ADD CONSTRAINT users_role_fkey
    FOREIGN KEY (role_id) REFERENCES auth.roles(id) ON DELETE SET NULL;

-- ============================================================
-- Hospital Devices table
-- ============================================================
CREATE TABLE auth.hospital_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id VARCHAR(255) UNIQUE NOT NULL,
    device_type auth.device_type NOT NULL,
    public_key TEXT,
    status auth.device_status DEFAULT 'ACTIVE',
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_certificate_update TIMESTAMP,
    user_id UUID NOT NULL,
    hospital_id UUID,
    CONSTRAINT hospital_devices_user_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);

CREATE INDEX idx_hospital_devices_device_id ON auth.hospital_devices(device_id);
CREATE INDEX idx_hospital_devices_status ON auth.hospital_devices(status);
CREATE INDEX idx_hospital_devices_user_id ON auth.hospital_devices(user_id);
CREATE INDEX idx_hospital_devices_hospital_id ON auth.hospital_devices(hospital_id);

-- ============================================================
-- Devices table (user devices for offline access)
-- ============================================================
CREATE TABLE auth.devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    device_type auth.device_type NOT NULL,
    device_name VARCHAR(100),
    device_identifier VARCHAR(255) UNIQUE,
    public_key TEXT,
    certificate TEXT,
    status auth.device_status DEFAULT 'ACTIVE',
    last_seen TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT devices_user_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);

CREATE INDEX idx_devices_user_id ON auth.devices(user_id);
CREATE INDEX idx_devices_status ON auth.devices(status);

-- ============================================================
-- Sessions table
-- ============================================================
CREATE TABLE auth.sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    device_id UUID,
    refresh_token_hash VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT sessions_user_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE,
    CONSTRAINT sessions_device_fkey FOREIGN KEY (device_id) REFERENCES auth.devices(id) ON DELETE SET NULL
);

CREATE INDEX idx_sessions_user_id ON auth.sessions(user_id);
CREATE INDEX idx_sessions_device_id ON auth.sessions(device_id);
CREATE INDEX idx_sessions_expires_at ON auth.sessions(expires_at);

-- ============================================================
-- Password Reset Tokens table
-- ============================================================
CREATE TABLE auth.password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT password_reset_user_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_token ON auth.password_reset_tokens(token);

-- ============================================================
-- Email Verification Tokens table
-- ============================================================
CREATE TABLE auth.email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT email_verification_user_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);

CREATE INDEX idx_email_verification_token ON auth.email_verification_tokens(token);

-- ============================================================
-- Device API Keys table
-- ============================================================
CREATE TABLE auth.device_api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_key VARCHAR(255) UNIQUE NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    hospital_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_device_api_keys_device_id ON auth.device_api_keys(device_id);
CREATE INDEX idx_device_api_keys_hospital_id ON auth.device_api_keys(hospital_id);
CREATE INDEX idx_device_api_keys_status ON auth.device_api_keys(status);

-- ============================================================
-- Device Authorization Codes table (OAuth device flow)
-- ============================================================
CREATE TABLE auth.device_authorization_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_code VARCHAR(255) UNIQUE NOT NULL,
    user_code VARCHAR(255) UNIQUE NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    hospital_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP NOT NULL,
    last_polled_at TIMESTAMP,
    interval_seconds INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_device_auth_codes_device_code ON auth.device_authorization_codes(device_code);
CREATE INDEX idx_device_auth_codes_user_code ON auth.device_authorization_codes(user_code);
CREATE INDEX idx_device_auth_codes_status ON auth.device_authorization_codes(status);
CREATE INDEX idx_device_auth_codes_expires_at ON auth.device_authorization_codes(expires_at);

-- ============================================================
-- Families table
-- ============================================================
CREATE TABLE auth.families (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    created_by UUID NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

CREATE INDEX idx_families_created_by ON auth.families(created_by);
CREATE INDEX idx_families_is_active ON auth.families(is_active);

-- ============================================================
-- Family Members table
-- ============================================================
CREATE TABLE auth.family_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,
    user_id UUID NOT NULL,
    patient_id UUID,
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    joined_at TIMESTAMP NOT NULL,
    invited_by UUID,
    date_of_birth DATE,
    relationship VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

CREATE INDEX idx_family_members_family_id ON auth.family_members(family_id);
CREATE INDEX idx_family_members_user_id ON auth.family_members(user_id);
CREATE INDEX idx_family_members_patient_id ON auth.family_members(patient_id);
CREATE INDEX idx_family_members_status ON auth.family_members(status);

-- ============================================================
-- Triggers
-- ============================================================
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION auth.update_updated_at_column();

CREATE TRIGGER update_devices_updated_at
    BEFORE UPDATE ON auth.devices
    FOR EACH ROW
    EXECUTE FUNCTION auth.update_updated_at_column();

CREATE OR REPLACE FUNCTION auth.update_roles_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_roles_updated_at
    BEFORE UPDATE ON auth.roles
    FOR EACH ROW
    EXECUTE FUNCTION auth.update_roles_updated_at();

-- ============================================================
-- Insert default system roles
-- ============================================================
INSERT INTO auth.roles (name, description, permissions, user_types, is_system_role) VALUES
    ('SUPER_ADMIN', 'Super Administrator with full system access',
     '["SUPER_ADMIN_ALL_ACCESS", "SUPER_ADMIN_MANAGE_ALL_HOSPITALS", "SUPER_ADMIN_MANAGE_SYSTEM_CONFIG", "SUPER_ADMIN_VIEW_ALL_AUDIT_LOGS", "SUPER_ADMIN_MANAGE_ROLES", "SUPER_ADMIN_MANAGE_SERVICE_CONFIG"]'::jsonb,
     ARRAY['SUPER_ADMIN'], TRUE),
    ('HOSPITAL_ADMIN', 'Hospital Administrator with hospital-wide access',
     '["HOSPITAL_ADMIN_ALL_ACCESS", "HOSPITAL_ADMIN_MANAGE_HOSPITAL_USERS", "HOSPITAL_ADMIN_MANAGE_HOSPITAL_ROLES", "HOSPITAL_ADMIN_VIEW_HOSPITAL_AUDIT_LOGS", "HOSPITAL_ADMIN_MANAGE_HOSPITAL_SETTINGS", "HOSPITAL_ADMIN_MANAGE_DEPARTMENTS", "HOSPITAL_ADMIN_VIEW_REPORTS"]'::jsonb,
     ARRAY['HOSPITAL_ADMIN'], TRUE),
    ('DOCTOR', 'Doctor with medical record access',
     '["PROVIDER_READ_PATIENT_RECORDS", "PROVIDER_WRITE_PATIENT_RECORDS", "PROVIDER_PRESCRIBE_MEDICATIONS", "PROVIDER_ORDER_TESTS", "PROVIDER_VIEW_ALL_PATIENTS"]'::jsonb,
     ARRAY['DOCTOR'], TRUE),
    ('NURSE', 'Nurse with vitals and observations access',
     '["NURSE_RECORD_VITALS", "NURSE_UPDATE_OBSERVATIONS", "NURSE_ADMINISTER_MEDICATIONS", "PROVIDER_READ_PATIENT_RECORDS"]'::jsonb,
     ARRAY['NURSE'], TRUE),
    ('RECEPTIONIST', 'Receptionist with patient registration access',
     '["RECEPTIONIST_REGISTER_PATIENTS", "RECEPTIONIST_SCHEDULE_APPOINTMENTS", "RECEPTIONIST_MANAGE_INSURANCE"]'::jsonb,
     ARRAY['RECEPTIONIST'], TRUE);

-- ============================================================
-- Table comments
-- ============================================================
COMMENT ON TABLE auth.hospitals IS 'Hospital entities in the system';
COMMENT ON TABLE auth.users IS 'All system users (patients, doctors, staff, admins)';
COMMENT ON TABLE auth.roles IS 'RBAC roles with permission sets';
COMMENT ON TABLE auth.user_roles IS 'User-to-role assignments';
COMMENT ON TABLE auth.devices IS 'Registered devices for offline access';
COMMENT ON TABLE auth.hospital_devices IS 'Hospital access devices for secure authentication';
COMMENT ON TABLE auth.sessions IS 'Active user sessions';
COMMENT ON TABLE auth.password_reset_tokens IS 'Password reset tokens (one-time use)';
COMMENT ON TABLE auth.email_verification_tokens IS 'Email verification tokens (one-time use)';
COMMENT ON TABLE auth.device_api_keys IS 'API keys for hospital device authentication';
COMMENT ON TABLE auth.device_authorization_codes IS 'OAuth 2.0 device flow authorization codes';
COMMENT ON TABLE auth.families IS 'Family accounts for managing multiple users';
COMMENT ON TABLE auth.family_members IS 'Members of family accounts';
COMMENT ON COLUMN auth.users.is_super_admin IS 'Flag for super admin users with system-wide access';
COMMENT ON COLUMN auth.users.permissions IS 'JSON array of permissions granted to user';
COMMENT ON COLUMN auth.users.role_id IS 'Primary role ID for the user';
