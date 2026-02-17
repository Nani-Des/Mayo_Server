package com.mayo.notification.dto;

import com.mayo.notification.entity.NotificationPriority;
import com.mayo.notification.entity.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private UUID id;
    private UUID userId;
    private String type;
    private String title;
    private String message;
    private String templateId;
    private Map<String, String> templateData;
    private NotificationPriority priority;
    private NotificationStatus status;
    private Instant scheduledAt;
    private Instant createdAt;
    private Instant sentAt;
    private Instant deliveredAt;
    private String failureReason;
}