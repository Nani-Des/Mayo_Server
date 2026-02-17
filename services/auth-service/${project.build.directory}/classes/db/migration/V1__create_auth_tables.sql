-- Auth Service Database Migration
-- V1: Complete database schema for auth service
-- Generated from Hibernate auto-schema and cleaned for production

-- Create update trigger function
CREATE OR REPLACE FUNCTION auth.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create enum types
CREATE TYPE auth.device_status AS ENUM ('ACTIVE', 'INACTIVE', 'REVOKED', 'EXPIRED');
CREATE TYPE auth.device_type AS ENUM ('MOBILE', 'DESKTOP', 'TABLET', 'IOT', 'WEARABLE');
CREATE TYPE auth.user_type AS ENUM ('PATIENT', 'DOCTOR', 'NURSE', 'ADMIN', 'RECEPTIONIST');

-- Users table
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
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON auth.users(email);
CREATE INDEX idx_users_ghana_card ON auth.users(ghana_card_id);
CREATE INDEX idx_users_user_type ON auth.users(user_type);

-- Hospital Devices table
CREATE TABLE auth.hospital_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id VARCHAR(255) UNIQUE NOT NULL,
    device_type auth.device_type NOT NULL,
    public_key TEXT,
    status auth.device_status DEFAULT 'ACTIVE',
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_certificate_update TIMESTAMP,
    user_id UUID NOT NULL,
    CONSTRAINT hospital_devices_user_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);

CREATE INDEX idx_hospital_devices_device_id ON auth.hospital_devices(device_id);
CREATE INDEX idx_hospital_devices_status ON auth.hospital_devices(status);
CREATE INDEX idx_hospital_devices_user_id ON auth.hospital_devices(user_id);

-- Devices table (user devices for offline access)
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

-- Sessions table
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

-- Password Reset Tokens table
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

-- Email Verification Tokens table
CREATE TABLE auth.email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT email_verification_user_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE
);

CREATE INDEX idx_email_verification_token ON auth.email_verification_tokens(token);

-- Device API Keys table
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

-- Device Authorization Codes table (for OAuth device flow)
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

-- Families table
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

-- Family Members table
CREATE TABLE auth.family_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,
    user_id UUID NOT NULL,
    patient_id UUID,
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    joined_at TIMESTAMP NOT NULL,
    invited_by UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INTEGER DEFAULT 0
);

CREATE INDEX idx_family_members_family_id ON auth.family_members(family_id);
CREATE INDEX idx_family_members_user_id ON auth.family_members(user_id);
CREATE INDEX idx_family_members_patient_id ON auth.family_members(patient_id);
CREATE INDEX idx_family_members_status ON auth.family_members(status);

-- Triggers
CREATE TRIGGER update_users_updated_at 
    BEFORE UPDATE ON auth.users 
    FOR EACH ROW 
    EXECUTE FUNCTION auth.update_updated_at_column();

CREATE TRIGGER update_devices_updated_at 
    BEFORE UPDATE ON auth.devices 
    FOR EACH ROW 
    EXECUTE FUNCTION auth.update_updated_at_column();

-- Table comments
COMMENT ON TABLE auth.users IS 'All system users (patients, doctors, staff)';
COMMENT ON TABLE auth.devices IS 'Registered devices for offline access';
COMMENT ON TABLE auth.hospital_devices IS 'Hospital access devices for secure authentication';
COMMENT ON TABLE auth.sessions IS 'Active user sessions';
COMMENT ON TABLE auth.password_reset_tokens IS 'Password reset tokens (one-time use)';
COMMENT ON TABLE auth.email_verification_tokens IS 'Email verification tokens (one-time use)';
COMMENT ON TABLE auth.device_api_keys IS 'API keys for hospital device authentication';
COMMENT ON TABLE auth.device_authorization_codes IS 'OAuth 2.0 device flow authorization codes';
COMMENT ON TABLE auth.families IS 'Family accounts for managing multiple users';
COMMENT ON TABLE auth.family_members IS 'Members of family accounts';
