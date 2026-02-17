# EMR Backend Service Interaction Diagrams

This document provides detailed service interaction diagrams for the Mayo EMR backend system, showing how microservices communicate through APIs, events, and data synchronization patterns.

## 1. Patient Record Creation and Sync

### Component Diagram

```mermaid
graph TB
    subgraph "Client Layer"
        Mobile[Mobile App]
        Desktop[Hospital Desktop]
        Tablet[Tablet App]
    end

    subgraph "API Gateway"
        Gateway[Spring Cloud Gateway]
    end

    subgraph "Core Services"
        PatientSvc[Patient Record Service]
        SyncSvc[Sync Service]
        AuditSvc[Await Service]
        NotificationSvc[Notification Service]
    end

    subgraph "Data Layer"
        Postgres[(PostgreSQL)]
        Redis[(Redis)]
    end

    subgraph "Event Layer"
        Kafka[Kafka Cluster]
    end

    Mobile --> Gateway
    Desktop --> Gateway
    Tablet --> Gateway

    Gateway --> PatientSvc
    Gateway --> SyncSvc

    PatientSvc --> Postgres
    SyncSvc --> Postgres
    SyncSvc --> Redis

    PatientSvc --> Kafka
    SyncSvc --> Kafka
    AuditSvc --> Kafka
    NotificationSvc --> Kafka

    Kafka --> AuditSvc
    Kafka --> NotificationSvc
    Kafka --> SyncSvc
```

### Sequence Diagram - Patient Record Creation

```mermaid
sequenceDiagram
    participant Mobile as Mobile App
    participant Gateway as API Gateway
    participant PatientSvc as Patient Record Service
    participant Kafka as Kafka
    participant SyncSvc as Sync Service
    participant AuditSvc as Audit Service
    participant NotificationSvc as Notification Service
    participant Postgres as PostgreSQL

    Mobile->>Gateway: POST /api/patient-records
    Gateway->>PatientSvc: Forward request
    PatientSvc->>Postgres: Create patient record
    Postgres-->>PatientSvc: Record created
    PatientSvc->>Kafka: Publish patient.record.created event
    PatientSvc-->>Gateway: Success response
    Gateway-->>Mobile: Success response

    Kafka->>SyncSvc: Consume patient.record.created
    SyncSvc->>SyncSvc: Process for sync delta
    SyncSvc->>Redis: Update sync state

    Kafka->>AuditSvc: Consume patient.record.created
    AuditSvc->>Postgres: Log audit event

    Kafka->>NotificationSvc: Consume patient.record.created
    NotificationSvc->>NotificationSvc: Check user preferences
    NotificationSvc->>NotificationSvc: Send push notification
```

### Sequence Diagram - Patient Record Sync

```mermaid
sequenceDiagram
    participant DeviceA as Device A
    participant SyncSvc as Sync Service
    participant Kafka as Kafka
    participant PatientSvc as Patient Record Service
    participant Postgres as PostgreSQL
    participant Redis as Redis

    DeviceA->>SyncSvc: POST /api/v1/sync/sync (delta sync request)
    SyncSvc->>Redis: Get last sync state for device
    SyncSvc->>Postgres: Query server changes since last sync
    SyncSvc->>SyncSvc: Compute deltas and resolve conflicts
    SyncSvc->>DeviceA: Send server changes
    DeviceA->>SyncSvc: Send device changes
    SyncSvc->>Postgres: Apply device changes
    SyncSvc->>Redis: Update sync state
    SyncSvc->>Kafka: Publish sync.completed event
    SyncSvc-->>DeviceA: Sync successful

    Kafka->>PatientSvc: Consume sync.completed
    PatientSvc->>PatientSvc: Update record versions
```

## 2. Device Authentication

### Component Diagram

```mermaid
graph TB
    subgraph "Client Layer"
        Mobile[Mobile App]
        Desktop[Hospital Desktop]
        Tablet[Tablet App]
    end

    subgraph "API Gateway"
        Gateway[Spring Cloud Gateway]
    end

    subgraph "Authentication Services"
        AuthSvc[Auth Service]
        SyncSvc[Sync Service]
    end

    subgraph "Data Layer"
        Postgres[(PostgreSQL)]
        Redis[(Redis)]
    end

    subgraph "Event Layer"
        Kafka[Kafka Cluster]
    end

    Mobile --> Gateway
    Desktop --> Gateway
    Tablet --> Gateway

    Gateway --> AuthSvc
    Gateway --> SyncSvc

    AuthSvc --> Postgres
    SyncSvc --> Postgres
    AuthSvc --> Redis
    SyncSvc --> Redis

    AuthSvc --> Kafka
    SyncSvc --> Kafka
```

### Sequence Diagram - Device Registration and Authentication

```mermaid
sequenceDiagram
    participant Mobile as Mobile App
    participant Gateway as API Gateway
    participant AuthSvc as Auth Service
    participant SyncSvc as Sync Service
    participant Kafka as Kafka
    participant Postgres as PostgreSQL
    participant Redis as Redis

    Mobile->>Gateway: POST /api/auth/login
    Gateway->>AuthSvc: Forward login request
    AuthSvc->>Postgres: Validate user credentials
    Postgres-->>AuthSvc: User validated
    AuthSvc->>AuthSvc: Generate JWT tokens
    AuthSvc-->>Gateway: Return access & refresh tokens
    Gateway-->>Mobile: Authentication successful

    Mobile->>Gateway: POST /api/devices/register (with JWT)
    Gateway->>AuthSvc: Forward device registration
    AuthSvc->>Postgres: Register device for user
    Postgres-->>AuthSvc: Device registered
    AuthSvc->>Kafka: Publish device.registered event
    AuthSvc-->>Gateway: Device registration successful
    Gateway-->>Mobile: Device registered

    Mobile->>Gateway: POST /api/v1/devices/pair
    Gateway->>SyncSvc: Forward device pairing
    SyncSvc->>SyncSvc: Validate pairing method (Bluetooth/WiFi/USB)
    SyncSvc->>Postgres: Create device pairing record
    SyncSvc->>Redis: Initialize sync state
    SyncSvc->>Kafka: Publish device.paired event
    SyncSvc-->>Gateway: Pairing successful
    Gateway-->>Mobile: Devices paired
```

## 3. Notification Flows

### Component Diagram

```mermaid
graph TB
    subgraph "Client Layer"
        Mobile[Mobile App]
        Desktop[Hospital Desktop]
        Tablet[Tablet App]
    end

    subgraph "API Gateway"
        Gateway[Spring Cloud Gateway]
    end

    subgraph "Notification Service"
        NotificationSvc[Notification Service]
    end

    subgraph "Data Layer"
        Postgres[(PostgreSQL)]
        Redis[(Redis)]
    end

    subgraph "Event Layer"
        Kafka[Kafka Cluster]
    end

    subgraph "External Services"
        FCM[Firebase Cloud Messaging]
        APNs[Apple Push Notification Service]
        Email[Email Service]
        SMS[SMS Gateway]
    end

    Mobile --> Gateway
    Desktop --> Gateway
    Tablet --> Gateway

    Gateway --> NotificationSvc

    NotificationSvc --> Postgres
    NotificationSvc --> Redis

    NotificationSvc --> Kafka
    Kafka --> NotificationSvc

    NotificationSvc --> FCM
    NotificationSvc --> APNs
    NotificationSvc --> Email
    NotificationSvc --> SMS
```

### Sequence Diagram - Notification Delivery Flow

```mermaid
sequenceDiagram
    participant Service as Any Service
    participant Kafka as Kafka
    participant NotificationSvc as Notification Service
    participant Postgres as PostgreSQL
    participant Redis as Redis
    participant FCM as Firebase/APNs
    participant Email as Email Service
    participant SMS as SMS Gateway

    Service->>Kafka: Publish notification event
    Kafka->>NotificationSvc: Consume notification event
    NotificationSvc->>Postgres: Retrieve user preferences
    Postgres-->>NotificationSvc: User preferences
    NotificationSvc->>Redis: Check delivery history
    Redis-->>NotificationSvc: Delivery status

    alt Push notification enabled
        NotificationSvc->>FCM: Send push notification
        FCM-->>NotificationSvc: Delivery confirmation
    end

    alt Email enabled
        NotificationSvc->>Email: Send email
        Email-->>NotificationSvc: Delivery status
    end

    alt SMS enabled
        NotificationSvc->>SMS: Send SMS
        SMS-->>NotificationSvc: Delivery status
    end

    NotificationSvc->>Postgres: Update notification status
    NotificationSvc->>Redis: Cache delivery result
    NotificationSvc->>Kafka: Publish delivery confirmation
```

### Sequence Diagram - Notification Preferences Management

```mermaid
sequenceDiagram
    participant Mobile as Mobile App
    participant Gateway as API Gateway
    participant NotificationSvc as Notification Service
    participant Postgres as PostgreSQL

    Mobile->>Gateway: PUT /api/v1/notifications/preferences/{userId}
    Gateway->>NotificationSvc: Update preferences
    NotificationSvc->>Postgres: Update user preferences
    Postgres-->>NotificationSvc: Preferences updated
    NotificationSvc-->>Gateway: Success response
    Gateway-->>Mobile: Preferences updated

    Mobile->>Gateway: POST /api/v1/notifications/preferences/{userId}/channels
    Gateway->>NotificationSvc: Add device channel
    NotificationSvc->>Postgres: Store device channel
    Postgres-->>NotificationSvc: Channel added
    NotificationSvc-->>Gateway: Success response
    Gateway-->>Mobile: Channel added
```

## 4. Audit Logging

### Component Diagram

```mermaid
graph TB
    subgraph "All Services"
        AuthSvc[Auth Service]
        PatientSvc[Patient Record Service]
        SyncSvc[Sync Service]
        HospitalSvc[Hospital Integration Service]
        NotificationSvc[Notification Service]
    end

    subgraph "Audit Service"
        AuditSvc[Await Service]
        AuditConsumer[Await Event Consumer]
        ComplianceEngine[Compliance Rule Engine]
        AlertSvc[Alert Service]
    end

    subgraph "Data Layer"
        Postgres[(PostgreSQL)]
        ES[(Elasticsearch)]
    end

    subgraph "Event Layer"
        Kafka[Kafka Cluster]
    end

    subgraph "Monitoring"
        Monitoring[Monitoring System]
    end

    AuthSvc --> Kafka
    PatientSvc --> Kafka
    SyncSvc --> Kafka
    HospitalSvc --> Kafka
    NotificationSvc --> Kafka

    Kafka --> AuditConsumer
    AuditConsumer --> AuditSvc
    AuditSvc --> Postgres
    AuditSvc --> ES
    AuditSvc --> ComplianceEngine
    ComplianceEngine --> AlertSvc
    AlertSvc --> Monitoring
    AlertSvc --> Kafka
```

### Sequence Diagram - Audit Event Processing

```mermaid
sequenceDiagram
    participant Service as Any Service
    participant Kafka as Kafka
    participant AuditConsumer as Audit Event Consumer
    participant AuditSvc as Audit Service
    participant Postgres as PostgreSQL
    participant ES as Elasticsearch
    participant ComplianceEngine as Compliance Rule Engine
    participant AlertSvc as Alert Service
    participant Monitoring as Monitoring System

    Service->>Kafka: Publish audit event
    Kafka->>AuditConsumer: Consume audit event
    AuditConsumer->>AuditSvc: Process audit event
    AuditSvc->>AuditSvc: Validate & enrich event
    AuditSvc->>Postgres: Store audit event
    AuditSvc->>ES: Index for search
    AuditSvc->>ComplianceEngine: Evaluate compliance rules

    alt Compliance violation detected
        ComplianceEngine->>AlertSvc: Trigger alert
        AlertSvc->>Monitoring: Send alert notification
        AlertSvc->>Kafka: Publish compliance alert event
    end

    AuditSvc->>Kafka: Publish audit.processed event
```

### Sequence Diagram - Audit Query and Reporting

```mermaid
sequenceDiagram
    participant Client as Client Application
    participant Gateway as API Gateway
    participant AuditSvc as Audit Service
    participant Postgres as PostgreSQL
    participant ES as Elasticsearch

    Client->>Gateway: GET /api/audit/events (with filters)
    Gateway->>AuditSvc: Query audit events
    AuditSvc->>Postgres: Query audit events
    Postgres-->>AuditSvc: Audit events data
    AuditSvc-->>Gateway: Return audit events
    Gateway-->>Client: Audit events response

    Client->>Gateway: GET /api/audit/events/search
    Gateway->>AuditSvc: Elasticsearch search
    AuditSvc->>ES: Search audit events
    ES-->>AuditSvc: Search results
    AuditSvc-->>Gateway: Return search results
    Gateway-->>Client: Search results

    Client->>Gateway: GET /api/audit/statistics
    Gateway->>AuditSvc: Get audit statistics
    AuditSvc->>Postgres: Aggregate statistics
    Postgres-->>AuditSvc: Statistics data
    AuditSvc-->>Gateway: Return statistics
    Gateway-->>Client: Audit statistics
```

## 5. Hospital Integration

### Component Diagram

```mermaid
graph TB
    subgraph "Hospital Systems"
        HIS[Hospital Information System]
        Devices[Medical Devices]
        PACS[PACS/DICOM Systems]
    end

    subgraph "API Gateway"
        Gateway[Spring Cloud Gateway]
    end

    subgraph "Integration Service"
        HospitalSvc[Hospital Integration Service]
        DeviceRegistry[Device Registry]
        DataTransfer[Data Transfer Handler]
        ActivityLogger[Activity Logger]
    end

    subgraph "Data Layer"
        Postgres[(PostgreSQL)]
    end

    subgraph "Event Layer"
        Kafka[Kafka Cluster]
    end

    subgraph "Supported Protocols"
        HL7[HL7 Interface]
        FHIR[FHIR Interface]
        DICOM[DICOM Interface]
        REST[REST API Interface]
    end

    HIS --> HL7
    Devices --> REST
    PACS --> DICOM

    HL7 --> HospitalSvc
    FHIR --> HospitalSvc
    DICOM --> HospitalSvc
    REST --> HospitalSvc

    Gateway --> HospitalSvc

    HospitalSvc --> DeviceRegistry
    HospitalSvc --> DataTransfer
    HospitalSvc --> ActivityLogger

    DeviceRegistry --> Postgres
    DataTransfer --> Postgres
    ActivityLogger --> Postgres

    HospitalSvc --> Kafka
```

### Sequence Diagram - Hospital Device Registration

```mermaid
sequenceDiagram
    participant Admin as Hospital Admin
    participant Gateway as API Gateway
    participant HospitalSvc as Hospital Integration Service
    participant Kafka as Kafka
    participant AuditSvc as Audit Service
    participant Postgres as PostgreSQL

    Admin->>Gateway: POST /api/v1/hospitals
    Gateway->>HospitalSvc: Register hospital
    HospitalSvc->>Postgres: Create hospital record
    Postgres-->>HospitalSvc: Hospital created
    HospitalSvc->>Kafka: Publish hospital.registered event
    HospitalSvc-->>Gateway: Success response
    Gateway-->>Admin: Hospital registered

    Admin->>Gateway: POST /api/v1/devices/register
    Gateway->>HospitalSvc: Register hospital device
    HospitalSvc->>Postgres: Create device record
    Postgres-->>HospitalSvc: Device registered
    HospitalSvc->>Kafka: Publish device.registered event
    HospitalSvc-->>Gateway: Success response
    Gateway-->>Admin: Device registered

    Kafka->>AuditSvc: Consume registration events
    AuditSvc->>Postgres: Log audit events
```

### Sequence Diagram - Data Transfer from Hospital Device

```mermaid
sequenceDiagram
    participant Device as Hospital Device
    participant HospitalSvc as Hospital Integration Service
    participant Kafka as Kafka
    participant PatientSvc as Patient Record Service
    participant AuditSvc as Audit Service
    participant Postgres as PostgreSQL

    Device->>HospitalSvc: Data transfer request (HL7/FHIR/DICOM)
    HospitalSvc->>HospitalSvc: Validate device authentication
    HospitalSvc->>Postgres: Log transfer session start
    HospitalSvc->>HospitalSvc: Process data format conversion
    HospitalSvc->>PatientSvc: POST /api/patient-records (via internal API)
    PatientSvc->>Postgres: Store patient data
    PatientSvc-->>HospitalSvc: Data stored successfully
    HospitalSvc->>Postgres: Update transfer session status
    HospitalSvc->>Kafka: Publish data.transfer.completed event
    HospitalSvc-->>Device: Transfer successful

    Kafka->>AuditSvc: Consume transfer events
    AuditSvc->>Postgres: Log comprehensive audit trail
```

### Sequence Diagram - Device Heartbeat and Monitoring

```mermaid
sequenceDiagram
    participant Device as Hospital Device
    participant HospitalSvc as Hospital Integration Service
    participant Kafka as Kafka
    participant Monitoring as Monitoring System
    participant Postgres as PostgreSQL

    Device->>HospitalSvc: POST /api/v1/devices/{deviceId}/heartbeat
    HospitalSvc->>Postgres: Update device last seen
    Postgres-->>HospitalSvc: Heartbeat recorded
    HospitalSvc->>HospitalSvc: Check device health status
    HospitalSvc->>Kafka: Publish device.heartbeat event
    HospitalSvc-->>Device: Heartbeat acknowledged

    Kafka->>Monitoring: Consume heartbeat events
    Monitoring->>Monitoring: Update device status dashboard

    alt Device offline threshold exceeded
        Monitoring->>Monitoring: Trigger offline alert
        Monitoring->>Kafka: Publish device.offline alert
    end