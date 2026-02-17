package com.mayo.events.topics;

/**
 * Kafka topic names for Mayo EMR events
 */
public class Topics {
    public static final String USER_EVENTS = "user-events";
    public static final String PATIENT_EVENTS = "patient-events";
    public static final String MEDICAL_RECORD_EVENTS = "medical-record-events";
    public static final String HOSPITAL_EVENTS = "hospital-events";
    public static final String SYNC_EVENTS = "sync-events";
    public static final String AUDIT_EVENTS = "audit-events";
    public static final String NOTIFICATION_EVENTS = "notification-events";
    public static final String FAMILY_EVENTS = "family-events";
    public static final String DEVICE_EVENTS = "device-events";
    public static final String APPOINTMENT_EVENTS = "appointment-events";
    
    private Topics() {
        // Utility class
    }
}
