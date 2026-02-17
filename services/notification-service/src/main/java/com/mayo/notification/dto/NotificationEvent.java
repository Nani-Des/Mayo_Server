package com.mayo.notification.dto;

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
public class NotificationEvent {

    private UUID userId;
    private String type;
    private String title;
    private String message;
    private String templateId;
    private Map<String, String> templateData;
    private String priority; // HIGH, NORMAL, LOW, URGENT
    private String[] channels; // PUSH, EMAIL, SMS
    private String scheduledAt; // ISO timestamp
    private Map<String, Object> metadata;
}