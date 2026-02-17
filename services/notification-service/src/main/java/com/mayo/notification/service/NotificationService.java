package com.mayo.notification.service;

import com.mayo.notification.entity.Notification;
import com.mayo.notification.entity.NotificationPriority;
import com.mayo.notification.entity.NotificationStatus;
import com.mayo.notification.entity.UserNotificationPreferences;
import com.mayo.notification.repository.NotificationRepository;
import com.mayo.notification.repository.UserNotificationPreferencesRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main notification service for sending and managing notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserNotificationPreferencesRepository preferencesRepository;
    private final ObjectMapper objectMapper;
    private final FcmService fcmService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final RestAuthClient restAuthClient;

    private static final Set<String> VALID_NOTIFICATION_TYPES = Set.of(
            "APPOINTMENT_REMINDER",
            "MEDICATION_REMINDER",
            "LAB_RESULTS",
            "SYSTEM_MAINTENANCE",
            "ACCOUNT_SECURITY",
            "BILLING",
            "GENERAL");

    @Transactional
    public Notification sendNotification(UUID userId, String type, String title, String message,
            String templateId, Map<String, String> templateData,
            NotificationPriority priority, List<String> channels,
            Instant scheduledAt) {
        log.info("Sending notification to user: {}, type: {}", userId, type);

        // Get user preferences
        Optional<UserNotificationPreferences> prefsOpt = preferencesRepository.findByUserIdAndNotificationType(userId,
                type);

        UserNotificationPreferences prefs = prefsOpt.orElse(createDefaultPreferences(userId, type));

        // Create notification
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .templateId(templateId)
                .templateData(templateData)
                .priority(priority)
                .status(scheduledAt != null ? NotificationStatus.PENDING : NotificationStatus.SENDING)
                .scheduledAt(scheduledAt)
                .build();

        notification = notificationRepository.save(notification);

        // Send immediately if not scheduled
        if (scheduledAt == null) {
            sendViaChannels(notification, prefs, channels);
        }

        return notification;
    }

    @Transactional
    public Notification scheduleNotification(UUID userId, String type, String title, String message,
            String templateId, Map<String, String> templateData,
            NotificationPriority priority, List<String> channels,
            Instant scheduledAt) {
        return sendNotification(userId, type, title, message, templateId, templateData,
                priority, channels, scheduledAt);
    }

    private void sendViaChannels(Notification notification, UserNotificationPreferences prefs,
            List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            channels = List.of("PUSH");
        }

        for (String channel : channels) {
            switch (channel.toUpperCase()) {
                case "PUSH":
                    if (prefs.isPushEnabled() && prefs.getFcmToken() != null) {
                        fcmService.sendPushNotification(notification, prefs.getFcmToken());
                    }
                    break;
                case "EMAIL":
                    if (prefs.isEmailEnabled() && prefs.getEmailAddress() != null) {
                        emailService.sendEmail(notification, prefs.getEmailAddress());
                    }
                    break;
                case "SMS":
                    if (prefs.isSmsEnabled() && prefs.getPhoneNumber() != null) {
                        smsService.sendSms(notification, prefs.getPhoneNumber());
                    }
                    break;
            }
        }
    }

    private UserNotificationPreferences createDefaultPreferences(UUID userId, String type) {
        // Fetch user profile to populate defaults
        RestAuthClient.UserProfile userProfile = restAuthClient.getUserById(userId);

        return UserNotificationPreferences.builder()
                .userId(userId)
                .notificationType(type)
                .pushEnabled(true)
                .emailEnabled(userProfile != null && userProfile.getEmail() != null)
                .smsEnabled(userProfile != null && userProfile.getPhoneNumber() != null)
                .emailAddress(userProfile != null ? userProfile.getEmail() : null)
                .phoneNumber(userProfile != null ? userProfile.getPhoneNumber() : null)
                .quietHoursEnabled(false)
                .language("en")
                .build();
    }

    public Optional<Notification> getNotificationById(UUID id) {
        return notificationRepository.findById(id);
    }

    public Page<Notification> getUserNotifications(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserId(userId, pageable);
    }

    public Page<Notification> getNotificationsByStatus(NotificationStatus status, Pageable pageable) {
        // This method needs a repository method - for now return empty page
        List<Notification> notifications = notificationRepository.findByStatus(status);
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), notifications.size());
        return new org.springframework.data.domain.PageImpl<>(
                notifications.subList(start, end), pageable, notifications.size());
    }

    public Page<Notification> getNotificationsByType(String type, Pageable pageable) {
        // This method needs a repository method - for now filter in memory
        List<Notification> all = notificationRepository.findAll();
        List<Notification> filtered = all.stream()
                .filter(n -> n.getType().equals(type))
                .collect(Collectors.toList());
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filtered.size());
        return new org.springframework.data.domain.PageImpl<>(
                filtered.subList(start, end), pageable, filtered.size());
    }

    @Transactional
    public void updateNotificationStatus(UUID id, NotificationStatus status) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setStatus(status);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAsRead(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setStatus(NotificationStatus.DELIVERED);
        notification.setDeliveredAt(Instant.now());
        notificationRepository.save(notification);
    }

    @Transactional
    public void deleteNotification(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notificationRepository.delete(notification);
    }

    public List<UserNotificationPreferences> getUserPreferences(UUID userId) {
        List<UserNotificationPreferences> preferences = preferencesRepository.findByUserId(userId);

        // If no preferences exist, create default preferences based on user profile
        if (preferences.isEmpty()) {
            preferences = createDefaultPreferencesFromUserProfile(userId);
        }

        return preferences;
    }

    private List<UserNotificationPreferences> createDefaultPreferencesFromUserProfile(UUID userId) {
        // Fetch user profile from auth service
        RestAuthClient.UserProfile userProfile = restAuthClient.getUserById(userId);

        if (userProfile == null) {
            log.warn("Could not fetch user profile for user: {}, creating minimal defaults", userId);
            // Create minimal defaults without user data
            UserNotificationPreferences defaultPrefs = UserNotificationPreferences.builder()
                    .userId(userId)
                    .notificationType("ALL")
                    .pushEnabled(true)
                    .emailEnabled(false)
                    .smsEnabled(false)
                    .quietHoursEnabled(false)
                    .language("en")
                    .build();
            UserNotificationPreferences saved = preferencesRepository.save(defaultPrefs);
            return List.of(saved);
        }

        // Create default preferences with user data
        UserNotificationPreferences defaultPrefs = UserNotificationPreferences.builder()
                .userId(userId)
                .notificationType("ALL")
                .pushEnabled(true)
                .emailEnabled(userProfile.getEmail() != null)
                .smsEnabled(userProfile.getPhoneNumber() != null)
                .emailAddress(userProfile.getEmail())
                .phoneNumber(userProfile.getPhoneNumber())
                .quietHoursEnabled(false)
                .language("en")
                .build();

        UserNotificationPreferences saved = preferencesRepository.save(defaultPrefs);
        log.info("Created default notification preferences for user: {}", userId);
        return List.of(saved);
    }

    @Transactional
    public UserNotificationPreferences updateUserPreferences(UUID userId, String notificationType,
            Boolean pushEnabled, Boolean emailEnabled,
            Boolean smsEnabled, Boolean quietHoursEnabled,
            LocalTime quietHoursStart, LocalTime quietHoursEnd,
            String language) {
        Optional<UserNotificationPreferences> existing = preferencesRepository.findByUserIdAndNotificationType(userId,
                notificationType);

        UserNotificationPreferences prefs = existing.orElse(
                UserNotificationPreferences.builder()
                        .userId(userId)
                        .notificationType(notificationType)
                        .build());

        if (pushEnabled != null)
            prefs.setPushEnabled(pushEnabled);
        if (emailEnabled != null)
            prefs.setEmailEnabled(emailEnabled);
        if (smsEnabled != null)
            prefs.setSmsEnabled(smsEnabled);
        if (quietHoursEnabled != null)
            prefs.setQuietHoursEnabled(quietHoursEnabled);
        if (quietHoursStart != null)
            prefs.setQuietHoursStart(quietHoursStart);
        if (quietHoursEnd != null)
            prefs.setQuietHoursEnd(quietHoursEnd);
        if (language != null)
            prefs.setLanguage(language);

        return preferencesRepository.save(prefs);
    }

    public boolean isQuietHours(UserNotificationPreferences prefs, LocalTime currentTime) {
        if (!prefs.isQuietHoursEnabled()) {
            return false;
        }

        LocalTime start = prefs.getQuietHoursStart();
        LocalTime end = prefs.getQuietHoursEnd();

        if (start == null || end == null) {
            return false;
        }

        // Handle overnight quiet hours (e.g., 22:00 to 08:00)
        if (start.isAfter(end)) {
            return currentTime.isAfter(start) || currentTime.isBefore(end);
        } else {
            return currentTime.isAfter(start) && currentTime.isBefore(end);
        }
    }

    public NotificationStatistics getNotificationStatistics() {
        long total = notificationRepository.count();
        long delivered = notificationRepository.countByStatus(NotificationStatus.DELIVERED);
        long failed = notificationRepository.countByStatus(NotificationStatus.SEND_FAILED);

        // Count by type - this needs a repository method
        long appointmentReminders = notificationRepository.findAll().stream()
                .filter(n -> "APPOINTMENT_REMINDER".equals(n.getType()))
                .count();

        return new NotificationStatistics(total, delivered, failed, appointmentReminders);
    }

    @Transactional
    public int retryFailedNotifications() {
        List<Notification> failed = notificationRepository.findByStatus(NotificationStatus.SEND_FAILED);
        int retried = 0;

        for (Notification notification : failed) {
            try {
                Optional<UserNotificationPreferences> prefsOpt = preferencesRepository.findByUserIdAndNotificationType(
                        notification.getUserId(), notification.getType());

                if (prefsOpt.isPresent()) {
                    notification.setStatus(NotificationStatus.SENDING);
                    notificationRepository.save(notification);
                    sendViaChannels(notification, prefsOpt.get(), List.of("PUSH"));
                    retried++;
                }
            } catch (Exception e) {
                log.error("Failed to retry notification: {}", notification.getId(), e);
            }
        }

        return retried;
    }

    @Transactional
    public int cleanupOldNotifications(int daysOld) {
        Instant cutoffDate = Instant.now().minusSeconds(86400L * daysOld);
        List<Notification> oldNotifications = notificationRepository.findOldNotifications(
                List.of(NotificationStatus.DELIVERED, NotificationStatus.SEND_FAILED),
                cutoffDate);
        int count = oldNotifications.size();
        notificationRepository.deleteAll(oldNotifications);
        return count;
    }

    @Transactional
    public List<Notification> sendBulkNotifications(List<UUID> userIds, String type, String title,
            String message, NotificationPriority priority,
            List<String> channels, String templateId) {
        List<Notification> results = new ArrayList<>();

        for (UUID userId : userIds) {
            try {
                Notification notification = sendNotification(userId, type, title, message,
                        templateId, Map.of(), priority, channels, null);
                results.add(notification);
            } catch (Exception e) {
                log.error("Failed to send notification to user: {}", userId, e);
            }
        }

        return results;
    }

    public boolean validateNotificationType(String type) {
        return VALID_NOTIFICATION_TYPES.contains(type);
    }

    public static class NotificationStatistics {
        private final long totalNotifications;
        private final long deliveredCount;
        private final long failedCount;
        private final long appointmentReminders;

        public NotificationStatistics(long totalNotifications, long deliveredCount,
                long failedCount, long appointmentReminders) {
            this.totalNotifications = totalNotifications;
            this.deliveredCount = deliveredCount;
            this.failedCount = failedCount;
            this.appointmentReminders = appointmentReminders;
        }

        public long getTotalNotifications() {
            return totalNotifications;
        }

        public long getDeliveredCount() {
            return deliveredCount;
        }

        public long getFailedCount() {
            return failedCount;
        }

        public long getAppointmentReminders() {
            return appointmentReminders;
        }
    }
}
