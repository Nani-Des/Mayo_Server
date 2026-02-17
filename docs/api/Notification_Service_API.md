# Notification Service API Documentation

## Overview

The Notification Service manages multi-channel notifications for the Mayo EMR system, supporting push notifications via Firebase Cloud Messaging (FCM) and Apple Push Notification Service (APNs), along with email and SMS fallbacks. It provides user preference management, template-based messaging, and delivery tracking.

## Base URL
```
http://localhost:8086/api/v1
```

## Authentication
All API endpoints require JWT authentication. Service-to-service communication uses API keys.

## User Preferences APIs

### Get User Notification Preferences

Retrieve notification preferences for a specific user.

```http
GET /notifications/preferences/{userId}
```

**Response:**
```json
{
  "userId": "uuid",
  "preferences": [
    {
      "type": "APPOINTMENT_REMINDER",
      "pushEnabled": true,
      "emailEnabled": true,
      "smsEnabled": false,
      "quietHoursEnabled": true,
      "quietHoursStart": "22:00",
      "quietHoursEnd": "08:00",
      "language": "en"
    },
    {
      "type": "MEDICATION_REMINDER",
      "pushEnabled": true,
      "emailEnabled": false,
      "smsEnabled": true,
      "quietHoursEnabled": false,
      "language": "en"
    }
  ],
  "channels": [
    {
      "type": "FCM",
      "token": "fcm_registration_token_here",
      "active": true,
      "lastUsed": "2024-01-01T10:00:00Z"
    },
    {
      "type": "EMAIL",
      "address": "user@example.com",
      "verified": true
    }
  ]
}
```

### Update User Notification Preferences

Update notification preferences for a user.

```http
PUT /notifications/preferences/{userId}
```

**Request Body:**
```json
{
  "preferences": [
    {
      "type": "APPOINTMENT_REMINDER",
      "pushEnabled": true,
      "emailEnabled": true,
      "smsEnabled": false,
      "quietHoursEnabled": true,
      "quietHoursStart": "22:00",
      "quietHoursEnd": "08:00",
      "language": "en"
    }
  ]
}
```

**Response:** 204 No Content

### Register Device Token

Register a device token for push notifications.

```http
POST /notifications/preferences/{userId}/channels
```

**Request Body:**
```json
{
  "type": "FCM",
  "token": "fcm_registration_token_here",
  "deviceInfo": {
    "platform": "ANDROID",
    "version": "12.0",
    "model": "Samsung Galaxy S21"
  }
}
```

**Response:**
```json
{
  "channelId": "uuid",
  "type": "FCM",
  "token": "fcm_registration_token_here",
  "active": true,
  "registeredAt": "2024-01-01T10:00:00Z"
}
```

### Unregister Device Token

Remove a device token.

```http
DELETE /notifications/preferences/{userId}/channels/{channelId}
```

**Response:** 204 No Content

## Notification Management APIs

### Send Notification

Send a notification to a user.

```http
POST /notifications/send
```

**Request Body:**
```json
{
  "userId": "uuid",
  "type": "APPOINTMENT_REMINDER",
  "title": "Appointment Reminder",
  "message": "You have an appointment with Dr. Smith tomorrow at 10:00 AM",
  "templateId": "appointment_reminder",
  "templateData": {
    "doctorName": "Dr. Smith",
    "appointmentTime": "10:00 AM",
    "appointmentDate": "2024-01-02"
  },
  "priority": "HIGH",
  "channels": ["PUSH", "EMAIL"],
  "scheduledAt": "2024-01-01T09:00:00Z",
  "metadata": {
    "appointmentId": "uuid",
    "patientId": "uuid"
  }
}
```

**Response:**
```json
{
  "notificationId": "uuid",
  "status": "QUEUED",
  "scheduledAt": "2024-01-01T09:00:00Z",
  "channels": ["PUSH", "EMAIL"]
}
```

### Schedule Notification

Schedule a notification for future delivery.

```http
POST /notifications/schedule
```

**Request Body:** Same as send notification, but `scheduledAt` is required.

**Response:** Same as send notification.

### Get Notification Status

Check the delivery status of a notification.

```http
GET /notifications/{notificationId}/status
```

**Response:**
```json
{
  "notificationId": "uuid",
  "status": "DELIVERED",
  "sentAt": "2024-01-01T09:00:00Z",
  "deliveredAt": "2024-01-01T09:00:05Z",
  "channels": [
    {
      "type": "PUSH",
      "status": "DELIVERED",
      "deliveredAt": "2024-01-01T09:00:05Z",
      "providerResponse": {
        "messageId": "fcm_message_id",
        "success": true
      }
    },
    {
      "type": "EMAIL",
      "status": "DELIVERED",
      "deliveredAt": "2024-01-01T09:00:10Z",
      "providerResponse": {
        "messageId": "sendgrid_message_id",
        "success": true
      }
    }
  ]
}
```

### Get User Notification History

Retrieve notification history for a user.

```http
GET /notifications/history/{userId}
```

**Query Parameters:**
- `startDate` (optional): Start date filter
- `endDate` (optional): End date filter
- `type` (optional): Notification type filter
- `status` (optional): Delivery status filter
- `page` (default: 0): Page number
- `size` (default: 20): Page size

**Response:**
```json
{
  "content": [
    {
      "id": "uuid",
      "type": "APPOINTMENT_REMINDER",
      "title": "Appointment Reminder",
      "status": "DELIVERED",
      "sentAt": "2024-01-01T09:00:00Z",
      "deliveredAt": "2024-01-01T09:00:05Z",
      "channels": ["PUSH"],
      "metadata": {
        "appointmentId": "uuid"
      }
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 150
}
```

## Template Management APIs

### Get Available Templates

Retrieve list of available notification templates.

```http
GET /notifications/templates
```

**Response:**
```json
{
  "templates": [
    {
      "id": "appointment_reminder",
      "type": "APPOINTMENT",
      "name": "Appointment Reminder",
      "description": "Reminder for upcoming appointments",
      "variables": ["doctorName", "appointmentTime", "appointmentDate"],
      "localizations": ["en", "fr", "es"],
      "channels": ["PUSH", "EMAIL", "SMS"]
    }
  ]
}
```

### Create Custom Template

Create a custom notification template.

```http
POST /notifications/templates
```

**Request Body:**
```json
{
  "id": "custom_reminder",
  "type": "GENERAL",
  "name": "Custom Reminder",
  "description": "Custom notification template",
  "localizations": {
    "en": {
      "title": "Reminder: {{title}}",
      "message": "{{message}}"
    },
    "fr": {
      "title": "Rappel: {{title}}",
      "message": "{{message}}"
    }
  },
  "channels": ["PUSH", "EMAIL"],
  "variables": ["title", "message"]
}
```

**Response:**
```json
{
  "id": "custom_reminder",
  "type": "GENERAL",
  "name": "Custom Reminder",
  "createdAt": "2024-01-01T10:00:00Z"
}
```

### Update Template

Update an existing notification template.

```http
PUT /notifications/templates/{templateId}
```

**Request Body:** Same as create template.

**Response:** 204 No Content

### Delete Template

Delete a custom notification template.

```http
DELETE /notifications/templates/{templateId}
```

**Response:** 204 No Content

## Bulk Operations APIs

### Send Bulk Notifications

Send notifications to multiple users.

```http
POST /notifications/bulk/send
```

**Request Body:**
```json
{
  "userIds": ["uuid1", "uuid2", "uuid3"],
  "type": "SYSTEM_MAINTENANCE",
  "title": "System Maintenance Notice",
  "message": "The system will be undergoing maintenance tonight from 2-4 AM GMT",
  "priority": "MEDIUM",
  "channels": ["PUSH", "EMAIL"],
  "scheduledAt": "2024-01-01T20:00:00Z"
}
```

**Response:**
```json
{
  "jobId": "uuid",
  "totalRecipients": 3,
  "status": "PROCESSING",
  "estimatedCompletion": "2024-01-01T20:05:00Z"
}
```

### Get Bulk Job Status

Check the status of a bulk notification job.

```http
GET /notifications/bulk/jobs/{jobId}
```

**Response:**
```json
{
  "jobId": "uuid",
  "status": "COMPLETED",
  "totalRecipients": 1000,
  "successful": 985,
  "failed": 15,
  "completionTime": "2024-01-01T20:05:30Z",
  "failures": [
    {
      "userId": "uuid",
      "reason": "Invalid email address",
      "channel": "EMAIL"
    }
  ]
}
```

## Notification Types

### Healthcare Notifications
- `APPOINTMENT_REMINDER`: Upcoming appointment alerts
- `APPOINTMENT_CONFIRMED`: Appointment confirmation
- `APPOINTMENT_CANCELLED`: Appointment cancellation
- `MEDICATION_REMINDER`: Medication schedule reminders
- `TEST_RESULTS_READY`: Lab results availability
- `PRESCRIPTION_READY`: Prescription pickup alerts

### System Notifications
- `SYSTEM_MAINTENANCE`: Maintenance window notifications
- `SECURITY_ALERT`: Security-related alerts
- `ACCOUNT_ACTIVITY`: Account activity notifications
- `DATA_EXPORT_READY`: Data export completion

### Family Account Notifications
- `FAMILY_INVITATION`: Family membership invitation
- `FAMILY_MEMBER_ADDED`: New family member notification
- `OWNERSHIP_TRANSFER`: Patient ownership transfer alerts
- `ACCESS_GRANTED`: Access permission granted
- `ACCESS_REVOKED`: Access permission revoked

## Channel Types

### Push Notifications (FCM/APNs)
- **FCM**: Firebase Cloud Messaging for Android and web
- **APNs**: Apple Push Notification Service for iOS
- **Features**: Rich notifications, data messages, topic-based messaging

### Email Notifications
- **Provider**: SendGrid/AWS SES integration
- **Features**: HTML templates, attachments, tracking
- **Fallback**: SMS if email delivery fails

### SMS Notifications
- **Provider**: Twilio/AWS SNS integration
- **Features**: International delivery, status tracking
- **Limitations**: 160 characters per message

## Delivery Priorities

- **CRITICAL**: Immediate delivery, all channels, bypass quiet hours
- **HIGH**: Fast delivery, multiple channels, respect quiet hours
- **MEDIUM**: Standard delivery, preferred channels
- **LOW**: Delayed delivery, single channel, respect quiet hours

## Localization Support

The service supports multiple languages with fallback to English:

```json
{
  "localizations": {
    "en": {
      "title": "Appointment Reminder",
      "message": "You have an appointment with {{doctorName}} on {{date}} at {{time}}"
    },
    "fr": {
      "title": "Rappel de Rendez-vous",
      "message": "Vous avez un rendez-vous avec {{doctorName}} le {{date}} à {{time}}"
    },
    "es": {
      "title": "Recordatorio de Cita",
      "message": "Tiene una cita con {{doctorName}} el {{date}} a las {{time}}"
    }
  }
}
```

## Error Handling

Standard error responses:

```json
{
  "timestamp": "2024-01-01T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid notification type",
  "path": "/api/v1/notifications/send"
}
```

## Rate Limiting

API rate limits:
- Send notification: 1000 requests/minute per user
- Bulk operations: 10 jobs/hour per user
- Template operations: 100 requests/minute

Rate limit headers:
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 950
X-RateLimit-Reset: 1640995200
```

## Webhook Integration

Configure webhooks for delivery status updates:

```http
POST /webhooks/delivery-status
```

**Headers:**
```
X-Webhook-Signature: sha256=signature
Content-Type: application/json
```

**Payload:**
```json
{
  "notificationId": "uuid",
  "channel": "PUSH",
  "status": "DELIVERED",
  "deliveredAt": "2024-01-01T10:00:05Z",
  "deviceInfo": {
    "platform": "ANDROID",
    "appVersion": "1.2.3"
  }
}
```

## Monitoring and Analytics

### Delivery Statistics

```http
GET /notifications/analytics/delivery
```

**Query Parameters:**
- `startDate`: Start date for analytics
- `endDate`: End date for analytics
- `type`: Notification type filter

**Response:**
```json
{
  "period": {
    "start": "2024-01-01T00:00:00Z",
    "end": "2024-01-31T23:59:59Z"
  },
  "totalSent": 10000,
  "totalDelivered": 9500,
  "deliveryRate": 0.95,
  "byChannel": {
    "PUSH": {
      "sent": 8000,
      "delivered": 7600,
      "rate": 0.95
    },
    "EMAIL": {
      "sent": 1500,
      "delivered": 1425,
      "rate": 0.95
    },
    "SMS": {
      "sent": 500,
      "delivered": 475,
      "rate": 0.95
    }
  },
  "byType": {
    "APPOINTMENT_REMINDER": {
      "sent": 5000,
      "delivered": 4750,
      "rate": 0.95
    }
  }
}
```

## Security Considerations

- End-to-end encryption for notification content
- Token encryption in database storage
- JWT authentication for API access
- Rate limiting to prevent abuse
- Audit logging for all notification operations
- PII minimization in notification content