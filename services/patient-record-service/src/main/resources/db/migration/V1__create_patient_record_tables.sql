-- Patient Record Service Database Migration
-- V1: Complete consolidated schema
-- Consolidates V1–V5 into a single clean migration

-- Function for updating updated_at timestamp
CREATE OR REPLACE FUNCTION patient_record.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Enum for record types
CREATE TYPE patient_record.record_type_enum AS ENUM ('DIAGNOSIS', 'PRESCRIPTION', 'LAB_TEST', 'VISIT', 'NOTE');

-- Enum for activity actions
CREATE TYPE patient_record.activity_action_enum AS ENUM ('CREATE', 'UPDATE', 'VIEW', 'DELETE');

-- ============================================================
-- Patients table
-- ============================================================
CREATE TABLE patient_record.patients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    medical_record_number VARCHAR(50) UNIQUE,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    date_of_birth DATE,
    gender VARCHAR(10),
    contact_info TEXT,
    owner_id UUID,
    family_member_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INT DEFAULT 0
);

CREATE INDEX idx_patients_medical_record_number ON patient_record.patients(medical_record_number);
CREATE INDEX idx_patients_email ON patient_record.patients(email);
CREATE INDEX idx_patients_owner_id ON patient_record.patients(owner_id);
CREATE INDEX idx_patients_family_member_id ON patient_record.patients(family_member_id);

-- ============================================================
-- Patient records table
-- ============================================================
CREATE TABLE patient_record.patient_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient_record.patients(id) ON DELETE CASCADE,
    record_type patient_record.record_type_enum NOT NULL,
    title VARCHAR(255),
    description TEXT,
    metadata JSONB,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    version INT DEFAULT 0
);

CREATE INDEX idx_patient_records_patient_id ON patient_record.patient_records(patient_id);
CREATE INDEX idx_patient_records_record_type ON patient_record.patient_records(record_type);
CREATE INDEX idx_patient_records_created_at ON patient_record.patient_records(created_at);

-- ============================================================
-- Activity tracking table
-- ============================================================
CREATE TABLE patient_record.activities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient_record.patients(id) ON DELETE CASCADE,
    record_id UUID NOT NULL REFERENCES patient_record.patient_records(id) ON DELETE CASCADE,
    record_type patient_record.record_type_enum NOT NULL,
    action patient_record.activity_action_enum NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id VARCHAR(255),
    device_id UUID,
    hospital_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_activities_patient_id ON patient_record.activities(patient_id);
CREATE INDEX idx_activities_record_id ON patient_record.activities(record_id);
CREATE INDEX idx_activities_record_type ON patient_record.activities(record_type);
CREATE INDEX idx_activities_action ON patient_record.activities(action);
CREATE INDEX idx_activities_timestamp ON patient_record.activities(timestamp);
CREATE INDEX idx_activities_user_id ON patient_record.activities(user_id);
CREATE INDEX idx_activities_device_id ON patient_record.activities(device_id);
CREATE INDEX idx_activities_hospital_id ON patient_record.activities(hospital_id);

-- ============================================================
-- Ownership transfers table
-- ============================================================
CREATE TABLE patient_record.ownership_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patient_record.patients(id),
    previous_owner_id UUID NOT NULL,
    new_owner_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED')),
    reason TEXT,
    initiated_by UUID NOT NULL,
    confirmed_by UUID,
    initiated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT DEFAULT 0
);

CREATE INDEX idx_ownership_transfers_patient_id ON patient_record.ownership_transfers(patient_id);
CREATE INDEX idx_ownership_transfers_status ON patient_record.ownership_transfers(status);
CREATE INDEX idx_ownership_transfers_initiated_by ON patient_record.ownership_transfers(initiated_by);
CREATE INDEX idx_ownership_transfers_new_owner_id ON patient_record.ownership_transfers(new_owner_id);

-- ============================================================
-- Lab results table
-- ============================================================
CREATE TABLE patient_record.lab_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_record_id UUID NOT NULL REFERENCES patient_record.patient_records(id) ON DELETE CASCADE,
    test_name VARCHAR(255) NOT NULL,
    test_code VARCHAR(50),
    category VARCHAR(100),
    value DECIMAL(10,2),
    unit VARCHAR(50),
    reference_range VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'CORRECTED')),
    performed_at TIMESTAMP,
    reported_at TIMESTAMP,
    performing_lab VARCHAR(255),
    ordering_provider VARCHAR(255),
    interpretation TEXT,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_lab_results_patient_record_id ON patient_record.lab_results(patient_record_id);
CREATE INDEX idx_lab_results_performed_at ON patient_record.lab_results(performed_at);
CREATE INDEX idx_lab_results_status ON patient_record.lab_results(status);
CREATE INDEX idx_lab_results_test_name ON patient_record.lab_results(test_name);

-- ============================================================
-- Imaging studies table
-- ============================================================
CREATE TABLE patient_record.imaging_studies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_record_id UUID NOT NULL REFERENCES patient_record.patient_records(id) ON DELETE CASCADE,
    study_type VARCHAR(255) NOT NULL,
    modality VARCHAR(100),
    body_part VARCHAR(100),
    study_description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'PRELIMINARY', 'FINAL')),
    performed_at TIMESTAMP,
    reported_at TIMESTAMP,
    performing_facility VARCHAR(255),
    ordering_provider VARCHAR(255),
    interpreting_provider VARCHAR(255),
    findings TEXT,
    impression TEXT,
    recommendations TEXT,
    accession_number VARCHAR(100),
    study_instance_uid VARCHAR(255),
    image_count INTEGER,
    radiation_dose VARCHAR(100),
    contrast_used BOOLEAN,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_imaging_studies_patient_record_id ON patient_record.imaging_studies(patient_record_id);
CREATE INDEX idx_imaging_studies_performed_at ON patient_record.imaging_studies(performed_at);
CREATE INDEX idx_imaging_studies_status ON patient_record.imaging_studies(status);
CREATE INDEX idx_imaging_studies_accession_number ON patient_record.imaging_studies(accession_number);

-- ============================================================
-- Vital signs table
-- ============================================================
CREATE TABLE patient_record.vital_signs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_record_id UUID NOT NULL REFERENCES patient_record.patient_records(id) ON DELETE CASCADE,
    recorded_at TIMESTAMP NOT NULL,
    recorded_by VARCHAR(255),
    location VARCHAR(100),
    temperature DECIMAL(4,1),
    temperature_unit VARCHAR(20) DEFAULT 'CELSIUS' CHECK (temperature_unit IN ('CELSIUS', 'FAHRENHEIT')),
    heart_rate INTEGER,
    respiratory_rate INTEGER,
    systolic_bp INTEGER,
    diastolic_bp INTEGER,
    oxygen_saturation DECIMAL(4,1),
    oxygen_supplement BOOLEAN,
    weight_kg DECIMAL(5,2),
    height_cm DECIMAL(5,2),
    bmi DECIMAL(4,1),
    pain_scale INTEGER CHECK (pain_scale >= 0 AND pain_scale <= 10),
    glucose_mg_dl INTEGER,
    position VARCHAR(20) CHECK (position IN ('LYING', 'SITTING', 'STANDING', 'SUPINE', 'PRONE', 'LEFT_LATERAL', 'RIGHT_LATERAL')),
    method VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_vital_signs_patient_record_id ON patient_record.vital_signs(patient_record_id);
CREATE INDEX idx_vital_signs_recorded_at ON patient_record.vital_signs(recorded_at);

-- ============================================================
-- Medications table
-- ============================================================
CREATE TABLE patient_record.medications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_record_id UUID NOT NULL REFERENCES patient_record.patient_records(id) ON DELETE CASCADE,
    medication_name VARCHAR(255) NOT NULL,
    generic_name VARCHAR(255),
    brand_name VARCHAR(255),
    strength VARCHAR(100),
    form VARCHAR(50),
    dosage VARCHAR(100),
    frequency VARCHAR(100),
    route VARCHAR(50),
    quantity INTEGER,
    refills INTEGER DEFAULT 0,
    prescribing_provider VARCHAR(255),
    prescribed_at TIMESTAMP,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PRESCRIBED' CHECK (status IN ('PRESCRIBED', 'ACTIVE', 'ON_HOLD', 'DISCONTINUED', 'COMPLETED', 'CANCELLED')),
    indication TEXT,
    instructions TEXT,
    side_effects TEXT,
    interactions TEXT,
    cost DECIMAL(8,2),
    insurance_covered BOOLEAN,
    pharmacy VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_medications_patient_record_id ON patient_record.medications(patient_record_id);
CREATE INDEX idx_medications_prescribed_at ON patient_record.medications(prescribed_at);
CREATE INDEX idx_medications_status ON patient_record.medications(status);
CREATE INDEX idx_medications_medication_name ON patient_record.medications(medication_name);

-- ============================================================
-- Allergies table
-- ============================================================
CREATE TABLE patient_record.allergies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_record_id UUID NOT NULL REFERENCES patient_record.patient_records(id) ON DELETE CASCADE,
    allergen VARCHAR(255) NOT NULL,
    allergen_type VARCHAR(20) CHECK (allergen_type IN ('DRUG', 'FOOD', 'ENVIRONMENTAL', 'LATEX', 'INSECT', 'OTHER')),
    reaction_severity VARCHAR(20) CHECK (reaction_severity IN ('MILD', 'MODERATE', 'SEVERE', 'LIFE_THREATENING')),
    reaction_description TEXT,
    symptoms TEXT,
    onset_date TIMESTAMP,
    reported_date TIMESTAMP,
    reported_by VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'RESOLVED', 'UNCONFIRMED')),
    verification_status VARCHAR(100),
    verification_date TIMESTAMP,
    verified_by VARCHAR(255),
    treatment TEXT,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_allergies_patient_record_id ON patient_record.allergies(patient_record_id);
CREATE INDEX idx_allergies_reported_date ON patient_record.allergies(reported_date);
CREATE INDEX idx_allergies_status ON patient_record.allergies(status);
CREATE INDEX idx_allergies_allergen_type ON patient_record.allergies(allergen_type);

-- ============================================================
-- Triggers
-- ============================================================
CREATE TRIGGER update_patients_updated_at
    BEFORE UPDATE ON patient_record.patients
    FOR EACH ROW EXECUTE FUNCTION patient_record.update_updated_at_column();

CREATE TRIGGER update_patient_records_updated_at
    BEFORE UPDATE ON patient_record.patient_records
    FOR EACH ROW EXECUTE FUNCTION patient_record.update_updated_at_column();

CREATE TRIGGER update_ownership_transfers_updated_at
    BEFORE UPDATE ON patient_record.ownership_transfers
    FOR EACH ROW EXECUTE FUNCTION patient_record.update_updated_at_column();

CREATE TRIGGER update_lab_results_updated_at
    BEFORE UPDATE ON patient_record.lab_results
    FOR EACH ROW EXECUTE FUNCTION patient_record.update_updated_at_column();

CREATE TRIGGER update_imaging_studies_updated_at
    BEFORE UPDATE ON patient_record.imaging_studies
    FOR EACH ROW EXECUTE FUNCTION patient_record.update_updated_at_column();

CREATE TRIGGER update_vital_signs_updated_at
    BEFORE UPDATE ON patient_record.vital_signs
    FOR EACH ROW EXECUTE FUNCTION patient_record.update_updated_at_column();

CREATE TRIGGER update_medications_updated_at
    BEFORE UPDATE ON patient_record.medications
    FOR EACH ROW EXECUTE FUNCTION patient_record.update_updated_at_column();

CREATE TRIGGER update_allergies_updated_at
    BEFORE UPDATE ON patient_record.allergies
    FOR EACH ROW EXECUTE FUNCTION patient_record.update_updated_at_column();

-- ============================================================
-- Comments
-- ============================================================
COMMENT ON TABLE patient_record.patients IS 'Patient demographic information';
COMMENT ON TABLE patient_record.patient_records IS 'Medical records and notes for patients';
COMMENT ON TABLE patient_record.activities IS 'Activity tracking for patient record operations';
COMMENT ON TABLE patient_record.ownership_transfers IS 'Records of patient ownership transfers between users';
COMMENT ON COLUMN patient_record.patients.owner_id IS 'User ID who owns this patient record';