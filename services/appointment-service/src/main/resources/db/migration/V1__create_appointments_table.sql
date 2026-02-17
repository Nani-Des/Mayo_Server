-- Create appointments table
CREATE TABLE IF NOT EXISTS appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID,
    family_member_id UUID,
    provider_id UUID,
    hospital_id UUID,
    appointment_date DATE NOT NULL,
    appointment_time TIME NOT NULL,
    duration INTEGER NOT NULL DEFAULT 30,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    type VARCHAR(20) NOT NULL,
    notes TEXT,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0
);

-- Create indexes for common queries
CREATE INDEX IF NOT EXISTS idx_appointments_patient_id ON appointments(patient_id);
CREATE INDEX IF NOT EXISTS idx_appointments_family_member_id ON appointments(family_member_id);
CREATE INDEX IF NOT EXISTS idx_appointments_provider_id ON appointments(provider_id);
CREATE INDEX IF NOT EXISTS idx_appointments_hospital_id ON appointments(hospital_id);
CREATE INDEX IF NOT EXISTS idx_appointments_status ON appointments(status);
CREATE INDEX IF NOT EXISTS idx_appointments_appointment_date ON appointments(appointment_date);

-- Add comments
COMMENT ON TABLE appointments IS 'Stores appointment information for patients';
COMMENT ON COLUMN appointments.patient_id IS 'UUID of the patient';
COMMENT ON COLUMN appointments.family_member_id IS 'UUID of the family member (if appointment is for a family member)';
COMMENT ON COLUMN appointments.provider_id IS 'UUID of the healthcare provider';
COMMENT ON COLUMN appointments.hospital_id IS 'UUID of the hospital/facility';
COMMENT ON COLUMN appointments.appointment_date IS 'Date of the appointment';
COMMENT ON COLUMN appointments.appointment_time IS 'Time of the appointment';
COMMENT ON COLUMN appointments.duration IS 'Duration in minutes';
COMMENT ON COLUMN appointments.status IS 'Status: SCHEDULED, COMPLETED, CANCELLED, NO_SHOW';
COMMENT ON COLUMN appointments.type IS 'Type: CHECKUP, FOLLOW_UP, CONSULTATION, PROCEDURE, EMERGENCY';
