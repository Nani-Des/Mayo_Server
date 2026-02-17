-- Create hospital_devices table
CREATE TABLE hospital_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id VARCHAR(255) NOT NULL UNIQUE,
    device_type VARCHAR(50) NOT NULL,
    hospital_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    protocol VARCHAR(100) NOT NULL,
    device_name VARCHAR(255),
    last_heartbeat TIMESTAMP,
    pairing_code VARCHAR(8) UNIQUE,
    registered_by UUID,
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_hospital_devices_hospital_id ON hospital_devices(hospital_id);
CREATE INDEX idx_hospital_devices_status ON hospital_devices(status);
CREATE INDEX idx_hospital_devices_pairing_code ON hospital_devices(pairing_code);
CREATE INDEX idx_hospital_devices_device_id ON hospital_devices(device_id);
CREATE INDEX idx_hospital_devices_registered_by ON hospital_devices(registered_by);

-- Add trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_hospital_devices_updated_at
    BEFORE UPDATE ON hospital_devices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
