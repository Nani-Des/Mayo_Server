package com.mayo.notification.service;

import com.mayo.notification.entity.Notification;
import com.mayo.notification.entity.NotificationStatus;
import com.mayo.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryTracker {

    private final NotificationRepository notificationRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public void trackDeliveryStatus(UUID notificationId, NotificationStatus status) {
        try {
            // Update database
            Notification notification = notificationRepository.findById(notificationId).orElse(null);
            if (notification != null) {
                notification.setStatus(status);
                if (status == NotificationStatus.SENT) {
                    notification.setSentAt(Instant.now());
                } else if (status == NotificationStatus.DELIVERED) {
                    notification.setDeliveredAt(Instant.now());
                }
                notificationRepository.save(notification);
            }

            // Update Redis cache
            String key = "notification:" + notificationId + ":status";
            redisTemplate.opsForValue().set(key, status.name(), Duration.ofDays(30));

            log.debug("Updated delivery status for notification {} to {}", notificationId, status);

        } catch (Exception e) {
            log.error("Failed to track delivery status for notification: {}", notificationId, e);
        }
    }

    public void trackDeliveryFailure(UUID notificationId, String failureReason) {
        try {
            Notification notification = notificationRepository.findById(notificationId).orElse(null);
            if (notification != null) {
                notification.setStatus(NotificationStatus.SEND_FAILED);
                notification.setFailureReason(failureReason);
                notificationRepository.save(notification);
            }

            // Update Redis cache
            String key = "notification:" + notificationId + ":status";
            redisTemplate.opsForValue().set(key, NotificationStatus.SEND_FAILED.name(), Duration.ofDays(30));

            log.warn("Tracked delivery failure for notification {}: {}", notificationId, failureReason);

        } catch (Exception e) {
            log.error("Failed to track delivery failure for notification: {}", notificationId, e);
        }
    }

    public NotificationStatus getDeliveryStatus(UUID notificationId) {
        try {
            String key = "notification:" + notificationId + ":status";
            String statusStr = redisTemplate.opsForValue().get(key);
            if (statusStr != null) {
                return NotificationStatus.valueOf(statusStr);
            }

            // Fallback to database
            Notification notification = notificationRepository.findById(notificationId).orElse(null);
            return notification != null ? notification.getStatus() : null;

        } catch (Exception e) {
            log.error("Failed to get delivery status for notification: {}", notificationId, e);
            return null;
        }
    }

    public void scheduleRetry(UUID notificationId, Instant retryAt) {
        try {
            String key = "retry:queue";
            redisTemplate.opsForZSet().add(key, notificationId.toString(), retryAt.toEpochMilli());

            log.debug("Scheduled retry for notification {} at {}", notificationId, retryAt);

        } catch (Exception e) {
            log.error("Failed to schedule retry for notification: {}", notificationId, e);
        }
    }
}