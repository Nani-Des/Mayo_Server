package com.mayo.notification.dto;

import com.mayo.notification.entity.NotificationPriority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationRequest {

    @NotNull
    private UUID userId;

    @NotBlank
    private String type;

    @NotBlank
    private String title;

    @NotBlank
    private String message;

    private String templateId;

    private Map<String, String> templateData;

    @NotNull
    private NotificationPriority priority;

    private List<String> channels; // PUSH, EMAIL, SMS

    private Instant scheduledAt;
}