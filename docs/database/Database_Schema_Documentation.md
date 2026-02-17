# Database Schema Documentation

This document provides comprehensive documentation of the database schemas for all implemented services in the Mayo EMR system.

## Table of Contents

- [Audit Service](#audit-service)
- [Auth Service](#auth-service)
- [Hospital Integration Service](#hospital-integration-service)
- [Notification Service](#notification-service)
- [Patient Record Service](#patient-record-service)
- [Sync Service](#sync-service)

---
## Audit Service

**Schema:** audit

### Tables

#### audit_events
Primary audit event storage with integrity hashing.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT uuid_generate_v4() | Unique identifier |
| event_id | VARCHAR(255) | UNIQUE NOT NULL | Unique event identifier |
| timestamp | TIMESTAMP WITH TIME ZONE | NOT NULL | Event timestamp |
| user_id | UUID |  | Associated user ID |
| device_id | UUID |  | Associated device ID |
| hospital_id | UUID |  | Associated hospital ID |
| session_id | VARCHAR(255) |  | Session identifier |
| action | audit_action | NOT NULL | Audit action type |
| resource_type | VARCHAR(100) | NOT NULL | Type of resource |
| resource_id | UUID |  | Resource identifier |
| patient_id | UUID |  | Associated patient ID |
| ip_address | INET |  | IP address |
| user_agent | TEXT |  | User agent string |
| location | JSONB |  | Location data |
| metadata | JSONB |  | Additional metadata |
| severity | VARCHAR(20) | DEFAULT 'INFO' | Event severity |
| compliance_flags | JSONB |  | Compliance flags |
| hash_value | VARCHAR(64) |  | SHA-256 hash for integrity |
| previous_event_hash | VARCHAR(64) |  | Chain hash for immutability |
| created_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Update timestamp |

**Indexes:**
- idx_audit_events_timestamp (timestamp)
- idx_audit_events_user_id (user_id)
- idx_audit_events_patient_id (patient_id)
- idx_audit_events_action (action)
- idx_audit_events_resource (resource_type, resource_id)
- idx_audit_events_compliance (compliance_flags) GIN
- idx_audit_events_hash (hash_value)
- idx_audit_events_severity (severity)

**Triggers:**
- update_audit_events_updated_at (BEFORE UPDATE)

#### compliance_rules
Configurable compliance monitoring rules.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT uuid_generate_v4() | Unique identifier |
| name | VARCHAR(255) | NOT NULL | Rule name |
| description | TEXT |  | Rule description |
| rule_type | compliance_rule_type | NOT NULL | Type of compliance rule |
| conditions | JSONB | NOT NULL | Rule conditions |
| actions | JSONB | NOT NULL | Rule actions |
| severity | compliance_severity | DEFAULT 'MEDIUM' | Rule severity |
| status | rule_status | DEFAULT 'DRAFT' | Rule status |
| created_by | UUID | NOT NULL | Creator user ID |
| created_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Update timestamp |
| version | INTEGER | DEFAULT 1 | Version number |

**Indexes:**
- idx_compliance_rules_status (status)
- idx_compliance_rules_type (rule_type)

**Triggers:**
- update_compliance_rules_updated_at (BEFORE UPDATE)

#### retention_policies
Data retention and archival policies.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT uuid_generate_v4() | Unique identifier |
| name | VARCHAR(255) | NOT NULL | Policy name |
| description | TEXT |  | Policy description |
| data_category | data_category | NOT NULL | Data category |
| retention_period | INTERVAL | NOT NULL | Retention period |
| retention_type | retention_type | DEFAULT 'DELETE' | Retention type |
| archive_location | VARCHAR(500) |  | Archive location |
| compression_enabled | BOOLEAN | DEFAULT true | Compression enabled |
| encryption_enabled | BOOLEAN | DEFAULT true | Encryption enabled |
| compliance_framework | VARCHAR(100) |  | Compliance framework |
| is_active | BOOLEAN | DEFAULT true | Active status |
| created_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Update timestamp |

**Triggers:**
- update_retention_policies_updated_at (BEFORE UPDATE)

#### compliance_reports
Generated compliance reports metadata.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT uuid_generate_v4() | Unique identifier |
| report_type | VARCHAR(100) | NOT NULL | Type of report |
| parameters | JSONB |  | Report parameters |
| status | report_status | DEFAULT 'GENERATING' | Report status |
| format | report_format | DEFAULT 'PDF' | Report format |
| file_path | VARCHAR(500) |  | File path |
| file_size | BIGINT |  | File size |
| record_count | INTEGER |  | Record count |
| generated_by | UUID | NOT NULL | Generator user ID |
| generated_at | TIMESTAMP WITH TIME ZONE |  | Generation timestamp |
| expires_at | TIMESTAMP WITH TIME ZONE |  | Expiration timestamp |
| created_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |

#### compliance_violations
Recorded compliance violations.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT uuid_generate_v4() | Unique identifier |
| rule_id | UUID | NOT NULL REFERENCES audit.compliance_rules(id) | Associated rule ID |
| event_id | UUID | NOT NULL REFERENCES audit.audit_events(id) | Associated event ID |
| violation_type | VARCHAR(100) | NOT NULL | Violation type |
| severity | compliance_severity | NOT NULL | Violation severity |
| description | TEXT |  | Violation description |
| details | JSONB |  | Violation details |
| resolved | BOOLEAN | DEFAULT false | Resolution status |
| resolved_at | TIMESTAMP WITH TIME ZONE |  | Resolution timestamp |
| resolved_by | UUID |  | Resolver user ID |
| created_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |

**Indexes:**
- idx_compliance_violations_rule (rule_id)
- idx_compliance_violations_event (event_id)
- idx_compliance_violations_resolved (resolved)

#### data_export_jobs
Data export job tracking.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT uuid_generate_v4() | Unique identifier |
| job_type | VARCHAR(50) | NOT NULL | Job type |
| parameters | JSONB |  | Job parameters |
| status | VARCHAR(20) | DEFAULT 'PENDING' | Job status |
| format | report_format | DEFAULT 'CSV' | Export format |
| file_path | VARCHAR(500) |  | File path |
| record_count | INTEGER |  | Record count |
| file_size | BIGINT |  | File size |
| requested_by | UUID | NOT NULL | Requester user ID |
| started_at | TIMESTAMP WITH TIME ZONE |  | Start timestamp |
| completed_at | TIMESTAMP WITH TIME ZONE |  | Completion timestamp |
| expires_at | TIMESTAMP WITH TIME ZONE |  | Expiration timestamp |
| created_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |

**Indexes:**
- idx_data_export_jobs_status (status)
- idx_data_export_jobs_requested_by (requested_by)

---
## Auth Service

### Tables

#### users
All system users (patients, doctors, staff).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| email | VARCHAR(255) | UNIQUE NOT NULL | User email |
| password_hash | VARCHAR(255) |  | Password hash |
| ghana_card_id | VARCHAR(20) | UNIQUE | Ghana Card ID |
| full_name | VARCHAR(255) | NOT NULL | Full name |
| phone_number | VARCHAR(20) |  | Phone number |
| user_type | user_type | NOT NULL DEFAULT 'PATIENT' | User type |
| supabase_user_id | UUID |  | Supabase user ID |
| email_verified | BOOLEAN | DEFAULT FALSE | Email verification status |
| is_active | BOOLEAN | DEFAULT TRUE | Active status |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Update timestamp |

**Indexes:**
- idx_users_email (email)
- idx_users_ghana_card (ghana_card_id)
- idx_users_user_type (user_type)

**Triggers:**
- update_users_updated_at (BEFORE UPDATE)

#### hospital_devices
Hospital access devices for secure authentication.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| device_id | VARCHAR(255) | UNIQUE NOT NULL | Device identifier |
| device_type | device_type | NOT NULL | Device type |
| public_key | TEXT |  | Public key |
| status | device_status | DEFAULT 'ACTIVE' | Device status |
| registered_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Registration timestamp |
| user_id | UUID | NOT NULL REFERENCES users(id) ON DELETE CASCADE | Associated user ID |

**Indexes:**
- idx_hospital_devices_user_id (user_id)
- idx_hospital_devices_device_id (device_id)
- idx_hospital_devices_status (status)

#### devices
Registered devices for offline access.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| user_id | UUID | NOT NULL REFERENCES users(id) ON DELETE CASCADE | Associated user ID |
| device_type | device_type | NOT NULL | Device type |
| device_name | VARCHAR(100) |  | Device name |
| device_identifier | VARCHAR(255) | UNIQUE | Device identifier |
| public_key | TEXT |  | Public key |
| certificate | TEXT |  | Certificate |
| status | device_status | DEFAULT 'ACTIVE' | Device status |
| last_seen | TIMESTAMP |  | Last seen timestamp |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Update timestamp |

**Indexes:**
- idx_devices_user_id (user_id)
- idx_devices_status (status)

**Triggers:**
- update_devices_updated_at (BEFORE UPDATE)

#### sessions
Active user sessions.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| user_id | UUID | NOT NULL REFERENCES users(id) ON DELETE CASCADE | Associated user ID |
| device_id | UUID | REFERENCES devices(id) ON DELETE SET NULL | Associated device ID |
| access_token_hash | VARCHAR(255) | NOT NULL | Access token hash |
| refresh_token_hash | VARCHAR(255) |  | Refresh token hash |
| ip_address | INET |  | IP address |
| user_agent | TEXT |  | User agent |
| expires_at | TIMESTAMP | NOT NULL | Expiration timestamp |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |

**Indexes:**
- idx_sessions_user_id (user_id)
- idx_sessions_device_id (device_id)
- idx_sessions_expires_at (expires_at)

#### password_reset_tokens
Password reset tokens (one-time use).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| user_id | UUID | NOT NULL REFERENCES users(id) ON DELETE CASCADE | Associated user ID |
| token | VARCHAR(255) | UNIQUE NOT NULL | Reset token |
| expires_at | TIMESTAMP | NOT NULL | Expiration timestamp |
| used | BOOLEAN | DEFAULT FALSE | Usage status |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |

**Indexes:**
- idx_password_reset_token (token)

#### email_verification_tokens
Email verification tokens (one-time use).

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| user_id | UUID | NOT NULL REFERENCES users(id) ON DELETE CASCADE | Associated user ID |
| token | VARCHAR(255) | UNIQUE NOT NULL | Verification token |
| expires_at | TIMESTAMP | NOT NULL | Expiration timestamp |
| used | BOOLEAN | DEFAULT FALSE | Usage status |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |

**Indexes:**
- idx_email_verification_token (token)

---
## Hospital Integration Service

**Schema:** hospital_integration

### Tables

#### hospitals
Hospital information and integration settings.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| hospital_id | VARCHAR(50) | UNIQUE NOT NULL | Hospital identifier |
| name | VARCHAR(255) | NOT NULL | Hospital name |
| address | TEXT |  | Hospital address |
| city | VARCHAR(100) |  | City |
| state | VARCHAR(100) |  | State |
| country | VARCHAR(100) |  | Country |
| postal_code | VARCHAR(20) |  | Postal code |
| phone | VARCHAR(50) |  | Phone number |
| email | VARCHAR(255) |  | Email |
| website | VARCHAR(255) |  | Website |
| status | VARCHAR(50) | NOT NULL DEFAULT 'ACTIVE' | Hospital status |
| integration_enabled | BOOLEAN | NOT NULL DEFAULT FALSE | Integration enabled |
| api_endpoint | TEXT |  | API endpoint |
| api_key | TEXT |  | API key |
| supported_data_types | TEXT |  | Supported data types |
| created_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Update timestamp |
| version | INTEGER | DEFAULT 0 | Version number |

#### hospital_devices
Registered hospital devices for data integration.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| device_id | VARCHAR(100) | UNIQUE NOT NULL | Device identifier |
| hospital_id | UUID | NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE | Associated hospital ID |
| device_type | VARCHAR(50) | NOT NULL | Device type |
| device_name | VARCHAR(255) | NOT NULL | Device name |
| manufacturer | VARCHAR(255) |  | Manufacturer |
| model | VARCHAR(255) |  | Model |
| serial_number | VARCHAR(255) |  | Serial number |
| firmware_version | VARCHAR(100) |  | Firmware version |
| ip_address | INET |  | IP address |
| mac_address | MACADDR |  | MAC address |
| connection_type | VARCHAR(50) | NOT NULL | Connection type |
| status | VARCHAR(50) | NOT NULL DEFAULT 'REGISTERED' | Device status |
| supported_protocols | TEXT |  | Supported protocols |
| supported_data_formats | TEXT |  | Supported data formats |
| last_heartbeat | TIMESTAMP WITH TIME ZONE |  | Last heartbeat |
| registered_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Registration timestamp |
| last_active_at | TIMESTAMP WITH TIME ZONE |  | Last active timestamp |
| created_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Update timestamp |
| version | INTEGER | DEFAULT 0 | Version number |

**Indexes:**
- idx_hospital_devices_hospital_id (hospital_id)
- idx_hospital_devices_status (status)
- idx_hospital_devices_device_type (device_type)

#### activities
Activity log for hospital device interactions and data access.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| hospital_id | UUID | NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE | Associated hospital ID |
| device_id | UUID | REFERENCES hospital_devices(id) ON DELETE SET NULL | Associated device ID |
| activity_type | VARCHAR(50) | NOT NULL | Activity type |
| activity_description | TEXT | NOT NULL | Activity description |
| user_id | UUID |  | Associated user ID |
| patient_id | UUID |  | Associated patient ID |
| record_id | VARCHAR(255) |  | Record identifier |
| record_type | VARCHAR(50) |  | Record type |
| ip_address | INET |  | IP address |
| user_agent | TEXT |  | User agent |
| metadata | TEXT |  | Metadata |
| timestamp | TIMESTAMP WITH TIME ZONE | NOT NULL DEFAULT CURRENT_TIMESTAMP | Activity timestamp |
| created_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |

**Indexes:**
- idx_activities_hospital_device (hospital_id, device_id)
- idx_activities_type_timestamp (activity_type, timestamp)
- idx_activities_timestamp (timestamp)
- idx_activities_user_id (user_id)
- idx_activities_patient_id (patient_id)

#### data_transfer_sessions
Data transfer session tracking and monitoring.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| session_id | VARCHAR(50) | UNIQUE NOT NULL | Session identifier |
| hospital_id | UUID | NOT NULL REFERENCES hospitals(id) ON DELETE CASCADE | Associated hospital ID |
| device_id | UUID | NOT NULL REFERENCES hospital_devices(id) ON DELETE CASCADE | Associated device ID |
| transfer_type | VARCHAR(20) | NOT NULL | Transfer type |
| data_type | VARCHAR(50) | NOT NULL | Data type |
| protocol | VARCHAR(50) | NOT NULL | Protocol |
| status | VARCHAR(50) | NOT NULL DEFAULT 'INITIATED' | Transfer status |
| total_records | BIGINT |  | Total records |
| processed_records | BIGINT | DEFAULT 0 | Processed records |
| failed_records | BIGINT | DEFAULT 0 | Failed records |
| data_size_bytes | BIGINT |  | Data size in bytes |
| transferred_bytes | BIGINT | DEFAULT 0 | Transferred bytes |
| source_endpoint | TEXT |  | Source endpoint |
| destination_endpoint | TEXT |  | Destination endpoint |
| initiated_by | UUID |  | Initiator user ID |
| error_message | TEXT |  | Error message |
| metadata | TEXT |  | Metadata |
| started_at | TIMESTAMP WITH TIME ZONE |  | Start timestamp |
| completed_at | TIMESTAMP WITH TIME ZONE |  | Completion timestamp |
| created_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP WITH TIME ZONE | DEFAULT CURRENT_TIMESTAMP | Update timestamp |
| version | INTEGER | DEFAULT 0 | Version number |

**Indexes:**
- idx_transfer_sessions_hospital_device (hospital_id, device_id)
- idx_transfer_sessions_status_timestamp (status, created_at)
- idx_transfer_sessions_session_status (session_id, status)

---
## Notification Service

### Tables

#### notifications
Core notification table.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY | Unique identifier |
| user_id | UUID | NOT NULL | Target user ID |
| type | VARCHAR(50) | NOT NULL | Notification type |
| title | VARCHAR(255) | NOT NULL | Notification title |
| message | TEXT | NOT NULL | Notification message |
| template_id | VARCHAR(100) |  | Template identifier |
| priority | VARCHAR(20) | NOT NULL DEFAULT 'NORMAL' | Notification priority |
| status | VARCHAR(20) | NOT NULL DEFAULT 'PENDING' | Notification status |
| scheduled_at | TIMESTAMP |  | Scheduled timestamp |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| sent_at | TIMESTAMP |  | Sent timestamp |
| delivered_at | TIMESTAMP |  | Delivered timestamp |
| failure_reason | TEXT |  | Failure reason |

**Indexes:**
- idx_notifications_user_id (user_id)
- idx_notifications_status (status)
- idx_notifications_scheduled_at (scheduled_at)
- idx_notifications_created_at (created_at)

#### user_notification_preferences
User notification preferences.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY | Unique identifier |
| user_id | UUID | NOT NULL | Associated user ID |
| notification_type | VARCHAR(50) | NOT NULL | Notification type |
| push_enabled | BOOLEAN | NOT NULL DEFAULT true | Push notifications enabled |
| email_enabled | BOOLEAN | NOT NULL DEFAULT false | Email notifications enabled |
| sms_enabled | BOOLEAN | NOT NULL DEFAULT false | SMS notifications enabled |
| email_address | VARCHAR(255) |  | Email address |
| phone_number | VARCHAR(20) |  | Phone number |
| fcm_token | TEXT |  | FCM token |
| apns_token | TEXT |  | APNs token |
| timezone | VARCHAR(50) |  | User timezone |
| language | VARCHAR(10) | DEFAULT 'en' | Preferred language |
| quiet_hours_enabled | BOOLEAN | NOT NULL DEFAULT false | Quiet hours enabled |
| quiet_hours_start | TIME |  | Quiet hours start |
| quiet_hours_end | TIME |  | Quiet hours end |

**Indexes:**
- idx_user_preferences_user_id (user_id)
- idx_user_preferences_type (notification_type)
- idx_user_preferences_user_type (user_id, notification_type) UNIQUE

#### notification_templates
Notification templates.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | VARCHAR(100) | PRIMARY KEY | Template identifier |
| type | VARCHAR(50) | NOT NULL | Template type |
| name | VARCHAR(255) | NOT NULL | Template name |
| description | TEXT |  | Template description |
| active | BOOLEAN | NOT NULL DEFAULT true | Active status |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Update timestamp |

#### template_localizations
Localized template content.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| template_id | VARCHAR(100) | NOT NULL | Template identifier |
| language_code | VARCHAR(10) | NOT NULL | Language code |
| content | TEXT | NOT NULL | Localized content |
| PRIMARY KEY | (template_id, language_code) |  | Composite primary key |
| FOREIGN KEY | (template_id) REFERENCES notification_templates(id) ON DELETE CASCADE |  | Foreign key constraint |

#### delivery_channels
Notification delivery channels.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY | Unique identifier |
| name | VARCHAR(50) | NOT NULL UNIQUE | Channel name |
| provider | VARCHAR(50) | NOT NULL | Provider name |
| active | BOOLEAN | NOT NULL DEFAULT true | Active status |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Update timestamp |

#### delivery_channel_config
Delivery channel configuration.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| channel_id | UUID | NOT NULL | Channel identifier |
| config_key | VARCHAR(100) | NOT NULL | Configuration key |
| config_value | TEXT |  | Configuration value |
| PRIMARY KEY | (channel_id, config_key) |  | Composite primary key |
| FOREIGN KEY | (channel_id) REFERENCES delivery_channels(id) ON DELETE CASCADE |  | Foreign key constraint |

#### notification_template_data
Template variable data.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| notification_id | UUID | NOT NULL | Notification identifier |
| key | VARCHAR(100) | NOT NULL | Variable key |
| value | TEXT |  | Variable value |
| PRIMARY KEY | (notification_id, key) |  | Composite primary key |
| FOREIGN KEY | (notification_id) REFERENCES notifications(id) ON DELETE CASCADE |  | Foreign key constraint |

---
## Patient Record Service

### Tables

#### patients
Patient demographic information.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| medical_record_number | VARCHAR(50) | UNIQUE | Medical record number |
| first_name | VARCHAR(255) | NOT NULL | First name |
| last_name | VARCHAR(255) | NOT NULL | Last name |
| email | VARCHAR(255) |  | Email address |
| date_of_birth | DATE |  | Date of birth |
| gender | VARCHAR(10) |  | Gender |
| contact_info | TEXT |  | Contact information |
| owner_id | UUID | REFERENCES users(id) | Owner user ID |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Update timestamp |
| version | INT | DEFAULT 0 | Version number |

**Indexes:**
- idx_patients_medical_record_number (medical_record_number)
- idx_patients_email (email)
- idx_patients_owner_id (owner_id)

**Triggers:**
- update_patients_updated_at (BEFORE UPDATE)

#### patient_records
Medical records and notes for patients.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| patient_id | UUID | NOT NULL REFERENCES patients(id) ON DELETE CASCADE | Associated patient ID |
| record_type | record_type_enum | NOT NULL | Record type |
| title | VARCHAR(255) |  | Record title |
| description | TEXT |  | Record description |
| metadata | JSONB |  | Additional metadata |
| created_by | VARCHAR(255) | NOT NULL | Creator user ID |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Update timestamp |
| version | INT | DEFAULT 0 | Version number |

**Indexes:**
- idx_patient_records_patient_id (patient_id)
- idx_patient_records_record_type (record_type)
- idx_patient_records_created_at (created_at)

**Triggers:**
- update_patient_records_updated_at (BEFORE UPDATE)

#### activities
Activity tracking for patient record operations.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| patient_id | UUID | NOT NULL REFERENCES patients(id) ON DELETE CASCADE | Associated patient ID |
| record_id | UUID | NOT NULL REFERENCES patient_records(id) ON DELETE CASCADE | Associated record ID |
| record_type | record_type_enum | NOT NULL | Record type |
| action | activity_action_enum | NOT NULL | Action performed |
| timestamp | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Action timestamp |
| user_id | VARCHAR(255) |  | Associated user ID |
| device_id | UUID |  | Associated device ID |
| hospital_id | UUID |  | Associated hospital ID |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | Creation timestamp |

**Indexes:**
- idx_activities_patient_id (patient_id)
- idx_activities_record_id (record_id)
- idx_activities_record_type (record_type)
- idx_activities_action (action)
- idx_activities_timestamp (timestamp)
- idx_activities_user_id (user_id)
- idx_activities_device_id (device_id)
- idx_activities_hospital_id (hospital_id)

#### ownership_transfers
Records of patient ownership transfers between users.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| patient_id | UUID | NOT NULL REFERENCES patients(id) | Associated patient ID |
| previous_owner_id | UUID | NOT NULL REFERENCES users(id) | Previous owner ID |
| new_owner_id | UUID | NOT NULL REFERENCES users(id) | New owner ID |
| status | VARCHAR(50) | NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED')) | Transfer status |
| reason | TEXT |  | Transfer reason |
| initiated_by | UUID | NOT NULL REFERENCES users(id) | Initiator user ID |
| confirmed_by | UUID | REFERENCES users(id) | Confirmer user ID |
| initiated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Initiation timestamp |
| confirmed_at | TIMESTAMP |  | Confirmation timestamp |
| completed_at | TIMESTAMP |  | Completion timestamp |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Update timestamp |
| version | INT | DEFAULT 0 | Version number |

**Indexes:**
- idx_ownership_transfers_patient_id (patient_id)
- idx_ownership_transfers_status (status)
- idx_ownership_transfers_initiated_by (initiated_by)
- idx_ownership_transfers_new_owner_id (new_owner_id)

**Triggers:**
- update_ownership_transfers_updated_at (BEFORE UPDATE)

#### lab_results
Laboratory test results.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| patient_record_id | UUID | NOT NULL REFERENCES patient_records(id) ON DELETE CASCADE | Associated record ID |
| test_name | VARCHAR(255) | NOT NULL | Test name |
| test_code | VARCHAR(50) |  | Test code |
| category | VARCHAR(100) |  | Test category |
| value | DECIMAL(10,2) |  | Test value |
| unit | VARCHAR(50) |  | Unit of measurement |
| reference_range | VARCHAR(255) |  | Reference range |
| status | VARCHAR(20) | NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'CORRECTED')) | Test status |
| performed_at | TIMESTAMP |  | Performance timestamp |
| reported_at | TIMESTAMP |  | Report timestamp |
| performing_lab | VARCHAR(255) |  | Performing laboratory |
| ordering_provider | VARCHAR(255) |  | Ordering provider |
| interpretation | TEXT |  | Result interpretation |
| notes | TEXT |  | Additional notes |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Update timestamp |
| version | INTEGER | NOT NULL DEFAULT 0 | Version number |

**Indexes:**
- idx_lab_results_patient_record_id (patient_record_id)
- idx_lab_results_performed_at (performed_at)
- idx_lab_results_status (status)
- idx_lab_results_test_name (test_name)

**Triggers:**
- update_lab_results_updated_at (BEFORE UPDATE)

#### imaging_studies
Medical imaging studies.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| patient_record_id | UUID | NOT NULL REFERENCES patient_records(id) ON DELETE CASCADE | Associated record ID |
| study_type | VARCHAR(255) | NOT NULL | Study type |
| modality | VARCHAR(100) |  | Imaging modality |
| body_part | VARCHAR(100) |  | Body part examined |
| study_description | TEXT |  | Study description |
| status | VARCHAR(20) | NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'PRELIMINARY', 'FINAL')) | Study status |
| performed_at | TIMESTAMP |  | Performance timestamp |
| reported_at | TIMESTAMP |  | Report timestamp |
| performing_facility | VARCHAR(255) |  | Performing facility |
| ordering_provider | VARCHAR(255) |  | Ordering provider |
| interpreting_provider | VARCHAR(255) |  | Interpreting provider |
| findings | TEXT |  | Study findings |
| impression | TEXT |  | Study impression |
| recommendations | TEXT |  | Recommendations |
| accession_number | VARCHAR(100) |  | Accession number |
| study_instance_uid | VARCHAR(255) |  | Study instance UID |
| image_count | INTEGER |  | Number of images |
| radiation_dose | VARCHAR(100) |  | Radiation dose |
| contrast_used | BOOLEAN |  | Contrast used |
| notes | TEXT |  | Additional notes |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Update timestamp |
| version | INTEGER | NOT NULL DEFAULT 0 | Version number |

**Indexes:**
- idx_imaging_studies_patient_record_id (patient_record_id)
- idx_imaging_studies_performed_at (performed_at)
- idx_imaging_studies_status (status)
- idx_imaging_studies_accession_number (accession_number)

**Triggers:**
- update_imaging_studies_updated_at (BEFORE UPDATE)

#### vital_signs
Patient vital signs measurements.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| patient_record_id | UUID | NOT NULL REFERENCES patient_records(id) ON DELETE CASCADE | Associated record ID |
| recorded_at | TIMESTAMP | NOT NULL | Recording timestamp |
| recorded_by | VARCHAR(255) |  | Recorder name |
| location | VARCHAR(100) |  | Recording location |
| temperature | DECIMAL(4,1) |  | Temperature |
| temperature_unit | VARCHAR(20) | DEFAULT 'CELSIUS' CHECK (temperature_unit IN ('CELSIUS', 'FAHRENHEIT')) | Temperature unit |
| heart_rate | INTEGER |  | Heart rate |
| respiratory_rate | INTEGER |  | Respiratory rate |
| systolic_bp | INTEGER |  | Systolic blood pressure |
| diastolic_bp | INTEGER |  | Diastolic blood pressure |
| oxygen_saturation | DECIMAL(4,1) |  | Oxygen saturation |
| oxygen_supplement | BOOLEAN |  | Oxygen supplement used |
| weight_kg | DECIMAL(5,2) |  | Weight in kg |
| height_cm | DECIMAL(5,2) |  | Height in cm |
| bmi | DECIMAL(4,1) |  | Body mass index |
| pain_scale | INTEGER | CHECK (pain_scale >= 0 AND pain_scale <= 10) | Pain scale (0-10) |
| glucose_mg_dl | INTEGER |  | Glucose level |
| position | VARCHAR(20) | CHECK (position IN ('LYING', 'SITTING', 'STANDING', 'SUPINE', 'PRONE', 'LEFT_LATERAL', 'RIGHT_LATERAL')) | Patient position |
| method | VARCHAR(100) |  | Measurement method |
| notes | TEXT |  | Additional notes |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Update timestamp |
| version | INTEGER | NOT NULL DEFAULT 0 | Version number |

**Indexes:**
- idx_vital_signs_patient_record_id (patient_record_id)
- idx_vital_signs_recorded_at (recorded_at)

**Triggers:**
- update_vital_signs_updated_at (BEFORE UPDATE)

#### medications
Patient medication records.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| patient_record_id | UUID | NOT NULL REFERENCES patient_records(id) ON DELETE CASCADE | Associated record ID |
| medication_name | VARCHAR(255) | NOT NULL | Medication name |
| generic_name | VARCHAR(255) |  | Generic name |
| brand_name | VARCHAR(255) |  | Brand name |
| strength | VARCHAR(100) |  | Medication strength |
| form | VARCHAR(50) |  | Medication form |
| dosage | VARCHAR(100) |  | Dosage instructions |
| frequency | VARCHAR(100) |  | Frequency |
| route | VARCHAR(50) |  | Administration route |
| quantity | INTEGER |  | Quantity |
| refills | INTEGER | DEFAULT 0 | Number of refills |
| prescribing_provider | VARCHAR(255) |  | Prescribing provider |
| prescribed_at | TIMESTAMP |  | Prescription timestamp |
| started_at | TIMESTAMP |  | Start timestamp |
| ended_at | TIMESTAMP |  | End timestamp |
| status | VARCHAR(20) | NOT NULL DEFAULT 'PRESCRIBED' CHECK (status IN ('PRESCRIBED', 'ACTIVE', 'ON_HOLD', 'DISCONTINUED', 'COMPLETED', 'CANCELLED')) | Medication status |
| indication | TEXT |  | Indication for use |
| instructions | TEXT |  | Patient instructions |
| side_effects | TEXT |  | Side effects |
| interactions | TEXT |  | Drug interactions |
| cost | DECIMAL(8,2) |  | Cost |
| insurance_covered | BOOLEAN |  | Insurance coverage |
| pharmacy | VARCHAR(255) |  | Pharmacy |
| notes | TEXT |  | Additional notes |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Update timestamp |
| version | INTEGER | NOT NULL DEFAULT 0 | Version number |

**Indexes:**
- idx_medications_patient_record_id (patient_record_id)
- idx_medications_prescribed_at (prescribed_at)
- idx_medications_status (status)
- idx_medications_medication_name (medication_name)

**Triggers:**
- update_medications_updated_at (BEFORE UPDATE)

#### allergies
Patient allergy records.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY DEFAULT gen_random_uuid() | Unique identifier |
| patient_record_id | UUID | NOT NULL REFERENCES patient_records(id) ON DELETE CASCADE | Associated record ID |
| allergen | VARCHAR(255) | NOT NULL | Allergen name |
| allergen_type | VARCHAR(20) | CHECK (allergen_type IN ('DRUG', 'FOOD', 'ENVIRONMENTAL', 'LATEX', 'INSECT', 'OTHER')) | Allergen type |
| reaction_severity | VARCHAR(20) | CHECK (reaction_severity IN ('MILD', 'MODERATE', 'SEVERE', 'LIFE_THREATENING')) | Reaction severity |
| reaction_description | TEXT |  | Reaction description |
| symptoms | TEXT |  | Symptoms |
| onset_date | TIMESTAMP |  | Onset date |
| reported_date | TIMESTAMP |  | Report date |
| reported_by | VARCHAR(255) |  | Reporter name |
| status | VARCHAR(20) | NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'RESOLVED', 'UNCONFIRMED')) | Allergy status |
| verification_status | VARCHAR(100) |  | Verification status |
| verification_date | TIMESTAMP |  | Verification date |
| verified_by | VARCHAR(255) |  | Verifier name |
| treatment | TEXT |  | Treatment |
| notes | TEXT |  | Additional notes |
| created_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | NOT NULL DEFAULT CURRENT_TIMESTAMP | Update timestamp |
| version | INTEGER | NOT NULL DEFAULT 0 | Version number |

**Indexes:**
- idx_allergies_patient_record_id (patient_record_id)
- idx_allergies_reported_date (reported_date)
- idx_allergies_status (status)
- idx_allergies_allergen_type (allergen_type)

**Triggers:**
- update_allergies_updated_at (BEFORE UPDATE)

---
## Sync Service

**Schema:** sync

### Tables

#### sync_sessions
Synchronization session tracking.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY | Unique identifier |
| device_id | VARCHAR(255) | NOT NULL | Device identifier |
| user_id | UUID | NOT NULL | Associated user ID |
| session_start | TIMESTAMP | NOT NULL | Session start timestamp |
| session_end | TIMESTAMP |  | Session end timestamp |
| status | VARCHAR(20) | NOT NULL | Session status |
| last_sync_timestamp | TIMESTAMP |  | Last sync timestamp |
| created_at | TIMESTAMP | NOT NULL | Creation timestamp |
| updated_at | TIMESTAMP | NOT NULL | Update timestamp |
| version | INTEGER | NOT NULL | Version number |

**Indexes:**
- idx_sync_sessions_user_device (user_id, device_id)
- idx_sync_sessions_status (status)

#### devices
Registered devices for synchronization.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY | Unique identifier |
| device_id | VARCHAR(255) | UNIQUE NOT NULL | Device identifier |
| device_type | VARCHAR(20) | NOT NULL | Device type |
| pairing_method | VARCHAR(20) | NOT NULL | Pairing method |
| user_id | UUID | NOT NULL | Associated user ID |
| public_key | TEXT |  | Public key |
| status | VARCHAR(20) | NOT NULL | Device status |
| last_seen | TIMESTAMP |  | Last seen timestamp |
| paired_at | TIMESTAMP | NOT NULL | Pairing timestamp |
| created_at | TIMESTAMP | NOT NULL | Creation timestamp |
| updated_at | TIMESTAMP | NOT NULL | Update timestamp |
| version | INTEGER | NOT NULL | Version number |

**Indexes:**
- idx_devices_user_id (user_id)
- idx_devices_device_id (device_id)
- idx_devices_status (status)

#### conflicts
Data synchronization conflicts.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY | Unique identifier |
| user_id | UUID | NOT NULL | Associated user ID |
| record_id | VARCHAR(255) | NOT NULL | Record identifier |
| record_type | VARCHAR(100) | NOT NULL | Record type |
| local_version | BIGINT | NOT NULL | Local version number |
| server_version | BIGINT | NOT NULL | Server version number |
| conflict_type | VARCHAR(30) | NOT NULL | Conflict type |
| resolution_status | VARCHAR(30) | NOT NULL | Resolution status |
| local_data | TEXT |  | Local data |
| server_data | TEXT |  | Server data |
| resolved_data | TEXT |  | Resolved data |
| resolved_at | TIMESTAMP |  | Resolution timestamp |
| created_at | TIMESTAMP | NOT NULL | Creation timestamp |
| updated_at | TIMESTAMP | NOT NULL | Update timestamp |
| version | INTEGER | NOT NULL | Version number |

**Indexes:**
- idx_conflicts_user_id (user_id)
- idx_conflicts_resolution_status (resolution_status)
- idx_conflicts_record_id (record_id)

#### delta_changes
Incremental data changes for synchronization.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY | Unique identifier |
| record_id | VARCHAR(255) | NOT NULL | Record identifier |
| record_type | VARCHAR(100) | NOT NULL | Record type |
| change_type | VARCHAR(20) | NOT NULL | Change type |
| version | BIGINT | NOT NULL | Version number |
| timestamp | TIMESTAMP | NOT NULL | Change timestamp |
| user_id | UUID | NOT NULL | Associated user ID |
| device_id | VARCHAR(255) |  | Associated device ID |
| data | TEXT |  | Change data |
| created_at | TIMESTAMP | NOT NULL | Creation timestamp |
| entity_version | INTEGER | NOT NULL | Entity version |

**Indexes:**
- idx_delta_changes_user_version (user_id, version)
- idx_delta_changes_record_type (record_type)
- idx_delta_changes_timestamp (timestamp)

---
