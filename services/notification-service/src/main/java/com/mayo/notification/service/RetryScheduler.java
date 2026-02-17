package com.mayo.notification.service;

import com.mayo.notification.entity.Notification;
import com.mayo.notification.entity.NotificationStatus;
import com.mayo.notification.entity.UserNotificationPreferences;
import com.mayo.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetryScheduler {

    private final RedisTemplate<String, String> redisTemplate;
    private final NotificationRepository notificationRepository;
    private final DeliveryRouter deliveryRouter;
    private final DeliveryTracker deliveryTracker;

    @Scheduled(fixedDelay = 30000) // Run every 30 seconds
    public void processRetryQueue() {
        try {
            String retryQueueKey = "retry:queue";
            long currentTime = Instant.now().toEpochMilli();

            // Get notifications that are ready for retry
            Set<String> readyForRetry = redisTemplate.opsForZSet()
                    .rangeByScore(retryQueueKey, 0, currentTime);

            if (readyForRetry == null || readyForRetry.isEmpty()) {
                return;
            }

            log.info("Processing {} notifications ready for retry", readyForRetry.size());

            for (String notificationIdStr : readyForRetry) {
                try {
                    UUID notificationId = UUID.fromString(notificationIdStr);
                    Notification notification = notificationRepository.findById(notificationId).orElse(null);

                    if (notification == null) {
                        log.warn("Notification {} not found, removing from retry queue", notificationId);
                        redisTemplate.opsForZSet().remove(retryQueueKey, notificationIdStr);
                        continue;
                    }

                    // Check retry count (stored in failure reason or separate field)
                    int retryCount = getRetryCount(notification);
                    int maxRetries = 6; // Configurable

                    if (retryCount >= maxRetries) {
                        log.warn("Notification {} exceeded max retries ({}), moving to dead letter queue",
                                notificationId, maxRetries);
                        moveToDeadLetterQueue(notification);
                        redisTemplate.opsForZSet().remove(retryQueueKey, notificationIdStr);
                        continue;
                    }

                    // Attempt retry
                    log.info("Retrying notification {} (attempt {})", notificationId, retryCount + 1);
                    List<UserNotificationPreferences> preferences = getUserPreferences(notification);

                    if (!preferences.isEmpty()) {
                        deliveryRouter.routeNotification(notification, preferences);
                    }

                    // Remove from retry queue
                    redisTemplate.opsForZSet().remove(retryQueueKey, notificationIdStr);

                } catch (Exception e) {
                    log.error("Failed to process retry for notification: {}", notificationIdStr, e);
                }
            }

        } catch (Exception e) {
            log.error("Failed to process retry queue", e);
        }
    }

    @Scheduled(fixedDelay = 60000) // Run every minute
    public void processScheduledNotifications() {
        try {
            List<Notification> scheduledNotifications = notificationRepository
                    .findScheduledNotifications(Instant.now(), NotificationStatus.PENDING);

            if (!scheduledNotifications.isEmpty()) {
                log.info("Processing {} scheduled notifications", scheduledNotifications.size());

                for (Notification notification : scheduledNotifications) {
                    try {
                        // Update status and route
                        notification.setStatus(NotificationStatus.SENDING);
                        notificationRepository.save(notification);

                        List<UserNotificationPreferences> preferences = getUserPreferences(notification);
                        if (!preferences.isEmpty()) {
                            deliveryRouter.routeNotification(notification, preferences);
                        }

                    } catch (Exception e) {
                        log.error("Failed to process scheduled notification: {}", notification.getId(), e);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to process scheduled notifications", e);
        }
    }

    private int getRetryCount(Notification notification) {
        // In a real implementation, you'd store retry count separately
        // For now, estimate based on failure reason or use a simple counter
        return 1; // Placeholder
    }

    private List<UserNotificationPreferences> getUserPreferences(Notification notification) {
        // This would need to be injected or retrieved
        // For now, return empty list as placeholder
        return List.of();
    }

    private void moveToDeadLetterQueue(Notification notification) {
        try {
            String deadLetterKey = "dead:letter:queue";
            redisTemplate.opsForZSet().add(deadLetterKey,
                    notification.getId().toString(),
                    Instant.now().toEpochMilli());

            notification.setStatus(NotificationStatus.SEND_FAILED);
            notification.setFailureReason("MOVED_TO_DEAD_LETTER_QUEUE");
            notificationRepository.save(notification);

            log.info("Moved notification {} to dead letter queue", notification.getId());

        } catch (Exception e) {
            log.error("Failed to move notification to dead letter queue: {}", notification.getId(), e);
        }
    }
}