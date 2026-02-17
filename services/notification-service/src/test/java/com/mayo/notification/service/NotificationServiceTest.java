package com.mayo.notification.service;

import com.mayo.notification.entity.Notification;
import com.mayo.notification.entity.NotificationPriority;
import com.mayo.notification.entity.NotificationStatus;
import com.mayo.notification.entity.UserNotificationPreferences;
import com.mayo.notification.repository.NotificationRepository;
import com.mayo.notification.repository.UserNotificationPreferencesRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserNotificationPreferencesRepository preferencesRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private FcmService fcmService;

    @Mock
    private EmailService emailService;

    @Mock
    private SmsService smsService;

    @InjectMocks
    private NotificationService notificationService;

    private UUID userId;
    private UUID notificationId;
    private Notification notification;
    private UserNotificationPreferences preferences;
    private JsonNode templateData;

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID();
        notificationId = UUID.randomUUID();

        templateData = mock(JsonNode.class);
        when(objectMapper.valueToTree(any())).thenReturn(templateData);

        notification = Notification.builder()
                .id(notificationId)
                .userId(userId)
                .type("APPOINTMENT_REMINDER")
                .title("Appointment Reminder")
                .message("You have an appointment tomorrow")
                .priority(NotificationPriority.HIGH)
                .status(NotificationStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        preferences = UserNotificationPreferences.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .notificationType("APPOINTMENT_REMINDER")
                .pushEnabled(true)
                .emailEnabled(true)
                .smsEnabled(false)
                .quietHoursEnabled(false)
                .language("en")
                .build();
    }

    @Test
    void sendNotification_ShouldSendSuccessfully() {
        // Given
        when(preferencesRepository.findByUserIdAndNotificationType(userId, "APPOINTMENT_REMINDER"))
                .thenReturn(Optional.of(preferences));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        // FCM service will be called internally by NotificationService

        Map<String, String> templateData = Map.of("doctorName", "Dr. Smith");

        // When
        Notification result = notificationService.sendNotification(
                userId, "APPOINTMENT_REMINDER", "Appointment Reminder",
                "You have an appointment with {{doctorName}}", "appointment_reminder",
                templateData, NotificationPriority.HIGH,
                List.of("PUSH", "EMAIL"), null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getType()).isEqualTo("APPOINTMENT_REMINDER");
        verify(notificationRepository).save(any(Notification.class));

    }

    @Test
    void sendNotification_UserPreferencesNotFound_ShouldUseDefaults() {
        // Given
        when(preferencesRepository.findByUserIdAndNotificationType(userId, "APPOINTMENT_REMINDER"))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        // When
        Notification result = notificationService.sendNotification(
                userId, "APPOINTMENT_REMINDER", "Appointment Reminder",
                "You have an appointment", null, Map.of(),
                NotificationPriority.HIGH, List.of("PUSH"), null);

        // Then
        assertThat(result).isNotNull();
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void scheduleNotification_ShouldScheduleSuccessfully() {
        // Given
        Instant scheduledTime = Instant.now().plusSeconds(3600);
        when(preferencesRepository.findByUserIdAndNotificationType(userId, "APPOINTMENT_REMINDER"))
                .thenReturn(Optional.of(preferences));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        // When
        Notification result = notificationService.scheduleNotification(
                userId, "APPOINTMENT_REMINDER", "Appointment Reminder",
                "You have an appointment", null, Map.of(),
                NotificationPriority.HIGH, List.of("PUSH"), scheduledTime);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getScheduledAt()).isEqualTo(scheduledTime);
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void getNotificationById_ShouldReturnNotification() {
        // Given
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        // When
        Optional<Notification> result = notificationService.getNotificationById(notificationId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(notificationId);
    }

    @Test
    void getUserNotifications_ShouldReturnPagedResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        List<Notification> notifications = List.of(notification);
        Page<Notification> notificationPage = new PageImpl<>(notifications, pageable, 1);

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(notificationPage);

        // When
        Page<Notification> result = notificationService.getUserNotifications(userId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    void getNotificationsByStatus_ShouldReturnFilteredResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        List<Notification> notifications = List.of(notification);
        Page<Notification> notificationPage = new PageImpl<>(notifications, pageable, 1);

        when(notificationRepository.findByStatusOrderByCreatedAtDesc(NotificationStatus.PENDING, pageable))
                .thenReturn(notificationPage);

        // When
        Page<Notification> result = notificationService
                .getNotificationsByStatus(NotificationStatus.PENDING, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void getNotificationsByType_ShouldReturnFilteredResults() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        List<Notification> notifications = List.of(notification);
        Page<Notification> notificationPage = new PageImpl<>(notifications, pageable, 1);

        when(notificationRepository.findByTypeOrderByCreatedAtDesc("APPOINTMENT_REMINDER", pageable))
                .thenReturn(notificationPage);

        // When
        Page<Notification> result = notificationService.getNotificationsByType("APPOINTMENT_REMINDER", pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getType()).isEqualTo("APPOINTMENT_REMINDER");
    }

    @Test
    void updateNotificationStatus_ShouldUpdateSuccessfully() {
        // Given
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        // When
        notificationService.updateNotificationStatus(notificationId, NotificationStatus.DELIVERED);

        // Then
        verify(notificationRepository)
                .save(argThat(n -> n.getStatus().equals(NotificationStatus.DELIVERED)));
    }

    @Test
    void updateNotificationStatus_NotificationNotFound_ShouldThrowException() {
        // Given
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> notificationService.updateNotificationStatus(notificationId,
                NotificationStatus.DELIVERED))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Notification not found");
    }

    @Test
    void markAsRead_ShouldUpdateReadStatus() {
        // Given
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        // When
        notificationService.markAsRead(notificationId);

        // Then
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void deleteNotification_ShouldDeleteSuccessfully() {
        // Given
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        // When
        notificationService.deleteNotification(notificationId);

        // Then
        verify(notificationRepository).delete(notification);
    }

    @Test
    void getUserPreferences_ShouldReturnPreferences() {
        // Given
        List<UserNotificationPreferences> prefs = List.of(preferences);
        when(preferencesRepository.findByUserId(userId)).thenReturn(prefs);

        // When
        List<UserNotificationPreferences> result = notificationService.getUserPreferences(userId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    void updateUserPreferences_ShouldUpdateSuccessfully() {
        // Given
        UserNotificationPreferences updatedPrefs = UserNotificationPreferences.builder()
                .id(preferences.getId())
                .userId(userId)
                .notificationType("APPOINTMENT_REMINDER")
                .pushEnabled(false)
                .emailEnabled(true)
                .smsEnabled(true)
                .build();

        when(preferencesRepository.findByUserIdAndNotificationType(userId, "APPOINTMENT_REMINDER"))
                .thenReturn(Optional.of(preferences));
        when(preferencesRepository.save(any(UserNotificationPreferences.class))).thenReturn(updatedPrefs);

        // When
        UserNotificationPreferences result = notificationService.updateUserPreferences(
                userId, "APPOINTMENT_REMINDER", false, true, true, null, null, null, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isPushEnabled()).isFalse();
        assertThat(result.isEmailEnabled()).isTrue();
        assertThat(result.isSmsEnabled()).isTrue();
    }

    @Test
    void isQuietHours_ShouldReturnTrueDuringQuietHours() {
        // Given
        UserNotificationPreferences quietPrefs = UserNotificationPreferences.builder()
                .quietHoursEnabled(true)
                .quietHoursStart(LocalTime.of(22, 0))
                .quietHoursEnd(LocalTime.of(8, 0))
                .build();

        // When
        boolean result = notificationService.isQuietHours(quietPrefs, LocalTime.of(23, 30));

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isQuietHours_ShouldReturnFalseOutsideQuietHours() {
        // Given
        UserNotificationPreferences quietPrefs = UserNotificationPreferences.builder()
                .quietHoursEnabled(true)
                .quietHoursStart(LocalTime.of(22, 0))
                .quietHoursEnd(LocalTime.of(8, 0))
                .build();

        // When
        boolean result = notificationService.isQuietHours(quietPrefs, LocalTime.of(12, 0));

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isQuietHours_ShouldReturnFalseWhenDisabled() {
        // Given
        UserNotificationPreferences quietPrefs = UserNotificationPreferences.builder()
                .quietHoursEnabled(false)
                .build();

        // When
        boolean result = notificationService.isQuietHours(quietPrefs, LocalTime.of(23, 30));

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void getNotificationStatistics_ShouldReturnStats() {
        // Given
        when(notificationRepository.countByStatus(NotificationStatus.DELIVERED)).thenReturn(1000L);
        when(notificationRepository.countByStatus(NotificationStatus.SEND_FAILED)).thenReturn(50L);
        when(notificationRepository.countByType("APPOINTMENT_REMINDER")).thenReturn(500L);
        when(notificationRepository.count()).thenReturn(2000L);

        // When
        var stats = notificationService.getNotificationStatistics();

        // Then
        assertThat(stats.getTotalNotifications()).isEqualTo(2000L);
        assertThat(stats.getDeliveredCount()).isEqualTo(1000L);
        assertThat(stats.getFailedCount()).isEqualTo(50L);
        assertThat(stats.getAppointmentReminders()).isEqualTo(500L);
    }

    @Test
    void retryFailedNotifications_ShouldRetrySuccessfully() {
        // Given
        Notification failedNotification = Notification.builder()
                .id(notificationId)
                .status(NotificationStatus.SEND_FAILED)
                .build();

        List<Notification> failedNotifications = List.of(failedNotification);
        when(notificationRepository.findByStatus(NotificationStatus.SEND_FAILED))
                .thenReturn(failedNotifications);
        when(notificationRepository.save(any(Notification.class))).thenReturn(failedNotification);

        // When
        int result = notificationService.retryFailedNotifications();

        // Then
        assertThat(result).isEqualTo(1);
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void cleanupOldNotifications_ShouldDeleteOldNotifications() {
        // Given
        Instant cutoffDate = Instant.now().minusSeconds(86400 * 30); // 30 days ago
        when(notificationRepository.findOldNotifications(anyList(), any(Instant.class)))
                .thenReturn(new ArrayList<>());

        // When
        int result = notificationService.cleanupOldNotifications(30);

        // Then
        assertThat(result).isEqualTo(0);
        verify(notificationRepository).deleteAll(anyList());
    }

    @Test
    void sendBulkNotifications_ShouldSendToMultipleUsers() {
        // Given
        List<UUID> userIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(preferencesRepository.findByUserIdAndNotificationType(any(UUID.class), anyString()))
                .thenReturn(Optional.of(preferences));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        // When
        List<Notification> results = notificationService.sendBulkNotifications(
                userIds, "SYSTEM_MAINTENANCE", "Maintenance Notice",
                "System will be down tonight", NotificationPriority.NORMAL,
                List.of("PUSH"), null);

        // Then
        assertThat(results).hasSize(2);
        verify(notificationRepository, times(2)).save(any(Notification.class));
    }

    @Test
    void validateNotificationType_ShouldAcceptValidType() {
        // When
        boolean result = notificationService.validateNotificationType("APPOINTMENT_REMINDER");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void validateNotificationType_ShouldRejectInvalidType() {
        // When
        boolean result = notificationService.validateNotificationType("INVALID_TYPE");

        // Then
        assertThat(result).isFalse();
    }
}