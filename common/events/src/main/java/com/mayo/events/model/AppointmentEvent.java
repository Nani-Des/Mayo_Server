package com.mayo.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentEvent {
    public enum AppointmentEventType {
        CREATED,
        UPDATED,
        CANCELLED,
        COMPLETED,
        STATUS_CHANGED
    }

    private UUID appointmentId;
    private UUID patientId;
    private UUID providerId;
    private UUID hospitalId;
    private AppointmentEventType eventType;
    private String appointmentDate; // ISO format
    private String appointmentTime;
    private String status;
    private Map<String, Object> metadata;
}
