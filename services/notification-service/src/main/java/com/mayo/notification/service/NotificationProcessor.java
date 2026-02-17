package com.mayo.notification.service;

import com.mayo.notification.dto.NotificationEvent;
import com.mayo.notification.entity.Notification;
import com.mayo.notification.entity.NotificationPriority;
import com.mayo.notification.entity.NotificationStatus;
import com.mayo.notification.entity.UserNotificationPreferences;
import com.mayo.notification.repository.NotificationRepository;
import com.mayo.notification.repository.UserNotificationPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProcessor {

    private final NotificationRepository notificationRepository;
    private final UserNotificationPreferencesRepository preferencesRepository;
    private final TemplateEngineService templateEngineService;
    private final DeliveryRouter deliveryRouter;
    private final DeliveryTracker deliveryTracker;

    @Transactional
    public void processNotificationEvent(NotificationEvent event) {
        try {
            log.info("Processing notification event for user: {}, type: {}", event.getUserId(), event.getType());

            // Get user preferences
            List<UserNotificationPreferences> preferences = getUserPreferences(event);

            // Check if user has any enabled channels for this notification type
            if (!hasEnabledChannels(preferences, event)) {
                log.info("No enabled channels for user {} and notification type {}", event.getUserId(), event.getType());
                return;
            }

            // Create notification entity
            Notification notification = createNotificationFromEvent(event);

            // Apply template if specified
            if (event.getTemplateId() != null) {
                notification = templateEngineService.applyTemplate(notification, event.getTemplateData());
            }

            // Save notification
            notification = notificationRepository.save(notification);

            // Route to delivery channels
            deliveryRouter.routeNotification(notification, preferences);

            log.info("Successfully processed notification: {}", notification.getId());

        } catch (Exception e) {
            log.error("Failed to process notification event: {}", event, e);
            throw new RuntimeException("Failed to process notification", e);
        }
    }

    private List<UserNotificationPreferences> getUserPreferences(NotificationEvent event) {
        return preferencesRepository.findByUserId(event.getUserId());
    }

    private boolean hasEnabledChannels(List<UserNotificationPreferences> preferences, NotificationEvent event) {
        return preferences.stream()
                .filter(pref -> pref.getNotificationType().equals(event.getType()) ||
                               pref.getNotificationType().equals("ALL"))
                .anyMatch(pref -> {
                    List<String> channels = Arrays.asList(event.getChannels() != null ? event.getChannels() : new String[]{"PUSH"});
                    return (pref.isPushEnabled() && channels.contains("PUSH")) ||
                           (pref.isEmailEnabled() && channels.contains("EMAIL")) ||
                           (pref.isSmsEnabled() && channels.contains("SMS"));
                });
    }

    private Notification createNotificationFromEvent(NotificationEvent event) {
        Instant scheduledAt = null;
        if (event.getScheduledAt() != null) {
            try {
                scheduledAt = Instant.parse(event.getScheduledAt());
            } catch (DateTimeParseException e) {
                log.warn("Invalid scheduled time format: {}", event.getScheduledAt());
            }
        }

        NotificationPriority priority = NotificationPriority.NORMAL;
        if (event.getPriority() != null) {
            try {
                priority = NotificationPriority.valueOf(event.getPriority().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid priority: {}, using NORMAL", event.getPriority());
            }
        }

        return Notification.builder()
                .userId(event.getUserId())
                .type(event.getType())
                .title(event.getTitle())
                .message(event.getMessage())
                .templateId(event.getTemplateId())
                .templateData(event.getTemplateData())
                .priority(priority)
                .status(scheduledAt != null ? NotificationStatus.PENDING : NotificationStatus.SENDING)
                .scheduledAt(scheduledAt)
                .build();
    }
}