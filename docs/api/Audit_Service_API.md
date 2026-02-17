# Audit Service API Documentation

## Overview

The Audit Service provides comprehensive audit logging, compliance monitoring, and regulatory reporting capabilities for the Mayo EMR system. It captures all system activities, maintains immutable audit trails, and supports compliance with HIPAA, GDPR, and Ghana Data Protection Act requirements.

## Base URL
```
http://localhost:8085/api
```

## Authentication
All API endpoints require JWT authentication with appropriate permissions:
- `AUDIT_ADMIN`: Full access to audit operations
- `COMPLIANCE_OFFICER`: Read access to audit data and compliance reports

## Audit Trail APIs

### Get Audit Events

Retrieve paginated audit events with filtering capabilities.

```http
GET /audit/events
```

**Query Parameters:**
- `userId` (optional): Filter by user UUID
- `patientId` (optional): Filter by patient UUID
- `action` (optional): Filter by audit action (e.g., "RECORD_ACCESSED", "PATIENT_CREATED")
- `resourceType` (optional): Filter by resource type (e.g., "PATIENT", "MEDICAL_RECORD")
- `startDate` (optional): Start date in ISO format (yyyy-MM-dd'T'HH:mm:ss'Z')
- `endDate` (optional): End date in ISO format
- `page` (default: 0): Page number
- `size` (default: 20): Page size
- `sortBy` (default: "timestamp"): Sort field
- `sortDir` (default: "desc"): Sort direction

**Response:**
```json
{
  "content": [
    {
      "id": "uuid",
      "eventId": "unique-event-id",
      "timestamp": "2024-01-01T10:00:00Z",
      "userId": "uuid",
      "deviceId": "uuid",
      "sessionId": "session-uuid",
      "action": "RECORD_ACCESSED",
      "resourceType": "PATIENT",
      "resourceId": "uuid",
      "patientId": "uuid",
      "ipAddress": "192.168.1.100",
      "userAgent": "Mozilla/5.0...",
      "location": {
        "country": "Ghana",
        "city": "Accra",
        "coordinates": [5.6037, -0.1870]
      },
      "metadata": {
        "recordType": "MEDICAL_RECORD",
        "accessReason": "Patient care"
      },
      "severity": "INFO",
      "complianceFlags": ["HIPAA", "GDPR"]
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "sorted": true,
      "field": "timestamp",
      "direction": "DESC"
    }
  },
  "totalElements": 150,
  "totalPages": 8
}
```

### Get Single Audit Event

Retrieve a specific audit event by ID.

```http
GET /audit/events/{eventId}
```

**Response:**
```json
{
  "id": "uuid",
  "eventId": "unique-event-id",
  "timestamp": "2024-01-01T10:00:00Z",
  "userId": "uuid",
  "action": "PATIENT_CREATED",
  "resourceType": "PATIENT",
  "resourceId": "uuid",
  "metadata": {
    "patientData": {
      "firstName": "John",
      "lastName": "Doe",
      "dateOfBirth": "1990-01-01"
    }
  }
}
```

### Search Audit Events

Perform full-text search across audit events using Elasticsearch.

```http
GET /audit/events/search
```

**Query Parameters:**
- `query`: Search query (e.g., "patient access denied")
- `page` (default: 0): Page number
- `size` (default: 20): Page size

**Response:**
```json
{
  "content": [
    {
      "id": "uuid",
      "eventId": "unique-event-id",
      "timestamp": "2024-01-01T10:00:00Z",
      "userId": "uuid",
      "action": "RECORD_ACCESSED",
      "resourceType": "PATIENT",
      "resourceId": "uuid",
      "metadata": {
        "searchScore": 0.85,
        "highlights": ["patient <em>access</em> denied"]
      }
    }
  ],
  "totalElements": 25
}
```

## Compliance APIs

### Generate Compliance Report

Create a compliance report for a specified time period.

```http
POST /compliance/reports
```

**Request Body:**
```json
{
  "reportType": "ACCESS_AUDIT",
  "parameters": {
    "startDate": "2024-01-01T00:00:00Z",
    "endDate": "2024-01-31T23:59:59Z",
    "userId": "uuid",
    "resourceType": "PATIENT"
  },
  "format": "PDF"
}
```

**Response:**
```json
{
  "id": "uuid",
  "reportType": "ACCESS_AUDIT",
  "status": "GENERATING",
  "format": "PDF",
  "createdAt": "2024-02-01T09:00:00Z"
}
```

### Get Compliance Report Status

Check the status of a compliance report generation.

```http
GET /compliance/reports/{reportId}
```

**Response:**
```json
{
  "id": "uuid",
  "reportType": "ACCESS_AUDIT",
  "status": "COMPLETED",
  "format": "PDF",
  "filePath": "/reports/access-audit-2024-01.pdf",
  "generatedAt": "2024-02-01T09:05:00Z",
  "expiresAt": "2024-02-15T09:05:00Z"
}
```

### Download Compliance Report

Download a completed compliance report.

```http
GET /compliance/reports/{reportId}/download
```

**Response:** Binary file download (PDF/CSV/JSON/XML)

### Get Compliance Violations

Retrieve compliance violations that require attention.

```http
GET /compliance/violations
```

**Query Parameters:**
- `severity` (optional): Filter by severity (LOW, MEDIUM, HIGH, CRITICAL)
- `startDate` (optional): Start date filter
- `endDate` (optional): End date filter
- `page` (default: 0): Page number
- `size` (default: 20): Page size

**Response:**
```json
{
  "content": [
    {
      "id": "uuid",
      "ruleId": "uuid",
      "eventId": "uuid",
      "severity": "HIGH",
      "description": "Unauthorized access attempt detected",
      "detectedAt": "2024-01-15T14:30:00Z",
      "status": "OPEN",
      "assignedTo": "uuid"
    }
  ],
  "totalElements": 5
}
```

## Data Export APIs

### Export Audit Data

Initiate a bulk export of audit data.

```http
POST /export/audit-data
```

**Request Body:**
```json
{
  "format": "CSV",
  "filters": {
    "startDate": "2024-01-01T00:00:00Z",
    "endDate": "2024-01-31T23:59:59Z",
    "actions": ["RECORD_ACCESSED", "PATIENT_CREATED"],
    "resourceTypes": ["PATIENT", "MEDICAL_RECORD"]
  },
  "compression": true
}
```

**Response:**
```json
{
  "jobId": "uuid",
  "status": "PROCESSING",
  "estimatedCompletion": "2024-02-01T10:30:00Z",
  "fileSize": "150MB"
}
```

### Get Export Status

Check the status of a data export job.

```http
GET /export/jobs/{jobId}
```

**Response:**
```json
{
  "jobId": "uuid",
  "status": "COMPLETED",
  "fileName": "audit-export-2024-01.zip",
  "fileSize": "145MB",
  "downloadUrl": "/export/jobs/uuid/download",
  "expiresAt": "2024-02-08T10:30:00Z"
}
```

### Download Exported Data

Download completed export file.

```http
GET /export/jobs/{jobId}/download
```

**Response:** Binary file download (ZIP containing exported data)

## Audit Event Types

### Authentication Events
- `USER_LOGIN`: User authentication
- `USER_LOGOUT`: User session termination
- `LOGIN_FAILED`: Failed authentication attempt
- `PASSWORD_CHANGED`: Password modification
- `TOKEN_REFRESHED`: JWT token refresh

### Patient Record Events
- `PATIENT_CREATED`: New patient record creation
- `PATIENT_UPDATED`: Patient information modification
- `PATIENT_DELETED`: Patient record deletion
- `RECORD_ACCESSED`: Medical record access
- `RECORD_MODIFIED`: Medical record changes
- `OWNERSHIP_TRANSFERRED`: Patient ownership change

### Family Account Events
- `FAMILY_CREATED`: Family account creation
- `MEMBER_ADDED`: Family member addition
- `MEMBER_REMOVED`: Family member removal
- `ACCESS_GRANTED`: Family member access to records
- `ACCESS_DENIED`: Access denial to family records

### System Events
- `BACKUP_COMPLETED`: System backup completion
- `MAINTENANCE_STARTED`: Maintenance mode activation
- `SECURITY_ALERT`: Security incident detection
- `CONFIGURATION_CHANGED`: System configuration modification

## Compliance Frameworks

The Audit Service supports multiple compliance frameworks:

### HIPAA (Health Insurance Portability and Accountability Act)
- Audit trail retention: 6 years
- Access logging for all PHI interactions
- Breach notification tracking

### GDPR (General Data Protection Regulation)
- Data subject access request logging
- Consent management audit trails
- Data processing activity records

### Ghana Data Protection Act
- Local data residency compliance
- Data export/import tracking
- Cross-border data transfer logging

## Error Responses

All APIs return standard HTTP status codes with detailed error information:

```json
{
  "timestamp": "2024-01-01T10:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Insufficient permissions to access audit data",
  "path": "/api/audit/events"
}
```

## Rate Limiting

API endpoints are rate-limited to prevent abuse:
- Standard users: 100 requests/minute
- Compliance officers: 500 requests/minute
- Audit admins: 1000 requests/minute

Rate limit headers are included in responses:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1640995200
```

## Data Retention

Audit data retention policies:
- Active audit logs: 7 years in PostgreSQL
- Elasticsearch indexes: 2 years for search performance
- Archived data: Indefinite storage in MinIO with compression
- Compliance reports: 10 years retention

## Security Considerations

- All audit data is encrypted at rest using AES-256
- Network communication secured with TLS 1.3
- Immutable audit logs prevent tampering
- Cryptographic hashing ensures data integrity
- Access controls based on role-based permissions