-- Notification Service Database Schema
-- Schema: notification

-- Create notifications table
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    template_id VARCHAR(100),
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    failure_reason TEXT
);

-- Create index on user_id for faster queries
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_scheduled_at ON notifications(scheduled_at);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);

-- Create user_notification_preferences table
CREATE TABLE user_notification_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    push_enabled BOOLEAN NOT NULL DEFAULT true,
    email_enabled BOOLEAN NOT NULL DEFAULT false,
    sms_enabled BOOLEAN NOT NULL DEFAULT false,
    email_address VARCHAR(255),
    phone_number VARCHAR(20),
    fcm_token TEXT,
    apns_token TEXT,
    timezone VARCHAR(50),
    language VARCHAR(10) DEFAULT 'en',
    quiet_hours_enabled BOOLEAN NOT NULL DEFAULT false,
    quiet_hours_start TIME,
    quiet_hours_end TIME
);

-- Create indexes for user preferences
CREATE INDEX idx_user_preferences_user_id ON user_notification_preferences(user_id);
CREATE INDEX idx_user_preferences_type ON user_notification_preferences(notification_type);
CREATE UNIQUE INDEX idx_user_preferences_user_type ON user_notification_preferences(user_id, notification_type);

-- Create notification_templates table
CREATE TABLE notification_templates (
    id VARCHAR(100) PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create template_localizations table for storing localized content
CREATE TABLE template_localizations (
    template_id VARCHAR(100) NOT NULL,
    language_code VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    PRIMARY KEY (template_id, language_code),
    FOREIGN KEY (template_id) REFERENCES notification_templates(id) ON DELETE CASCADE
);

-- Create delivery_channels table
CREATE TABLE delivery_channels (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    provider VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create delivery_channel_config table for storing provider configurations
CREATE TABLE delivery_channel_config (
    channel_id UUID NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT,
    PRIMARY KEY (channel_id, config_key),
    FOREIGN KEY (channel_id) REFERENCES delivery_channels(id) ON DELETE CASCADE
);

-- Create notification_template_data table for storing template variables
CREATE TABLE notification_template_data (
    notification_id UUID NOT NULL,
    key VARCHAR(100) NOT NULL,
    value TEXT,
    PRIMARY KEY (notification_id, key),
    FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE
);

-- Insert default delivery channels
INSERT INTO delivery_channels (id, name, provider, active) VALUES
(gen_random_uuid(), 'FCM', 'Firebase', true),
(gen_random_uuid(), 'APNs', 'Apple', true),
(gen_random_uuid(), 'EMAIL', 'SendGrid', true),
(gen_random_uuid(), 'SMS', 'Twilio', true);

-- Insert sample notification templates
INSERT INTO notification_templates (id, type, name, description, active) VALUES
('appointment_reminder', 'APPOINTMENT', 'Appointment Reminder', 'Template for appointment reminders', true),
('medication_reminder', 'MEDICATION', 'Medication Reminder', 'Template for medication reminders', true),
('sync_complete', 'SYNC', 'Sync Complete', 'Template for sync completion notifications', true);

-- Insert sample template localizations (English)
INSERT INTO template_localizations (template_id, language_code, content) VALUES
('appointment_reminder', 'en', '{"title": "Appointment Reminder", "message": "You have an appointment with {{doctorName}} on {{appointmentDate}} at {{appointmentTime}}"}'),
('medication_reminder', 'en', '{"title": "Medication Reminder", "message": "Time to take your {{medicationName}} - {{dosage}}"}'),
('sync_complete', 'en', '{"title": "Sync Complete", "message": "Your data has been successfully synchronized across all devices"}');

-- Insert sample template localizations (French)
INSERT INTO template_localizations (template_id, language_code, content) VALUES
('appointment_reminder', 'fr', '{"title": "Rappel de Rendez-vous", "message": "Vous avez un rendez-vous avec {{doctorName}} le {{appointmentDate}} à {{appointmentTime}}"}'),
('medication_reminder', 'fr', '{"title": "Rappel Médicament", "message": "Il est temps de prendre votre {{medicationName}} - {{dosage}}"}'),
('sync_complete', 'fr', '{"title": "Synchronisation Terminée", "message": "Vos données ont été synchronisées avec succès sur tous vos appareils"}');