package com.mayo.notification.service;

import com.mayo.notification.entity.NotificationStatus;
import com.mayo.notification.repository.NotificationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationMetricsService {

    private final MeterRegistry meterRegistry;
    private final NotificationRepository notificationRepository;

    // Counters for different operations
    private Counter notificationsSent;
    private Counter notificationsDelivered;
    private Counter notificationsFailed;
    private Counter pushNotificationsSent;
    private Counter emailNotificationsSent;
    private Counter smsNotificationsSent;

    // Gauges for current state
    private Gauge pendingNotifications;
    private Gauge retryQueueSize;
    private Gauge deadLetterQueueSize;

    @PostConstruct
    public void init() {
        // Initialize counters
        notificationsSent = Counter.builder("notification.sent.total")
                .description("Total number of notifications sent")
                .register(meterRegistry);

        notificationsDelivered = Counter.builder("notification.delivered.total")
                .description("Total number of notifications delivered")
                .register(meterRegistry);

        notificationsFailed = Counter.builder("notification.failed.total")
                .description("Total number of notifications failed")
                .register(meterRegistry);

        pushNotificationsSent = Counter.builder("notification.push.sent.total")
                .description("Total number of push notifications sent")
                .register(meterRegistry);

        emailNotificationsSent = Counter.builder("notification.email.sent.total")
                .description("Total number of email notifications sent")
                .register(meterRegistry);

        smsNotificationsSent = Counter.builder("notification.sms.sent.total")
                .description("Total number of SMS notifications sent")
                .register(meterRegistry);

        // Initialize gauges
        pendingNotifications = Gauge.builder("notification.pending.current", this::getPendingNotificationsCount)
                .description("Current number of pending notifications")
                .register(meterRegistry);

        retryQueueSize = Gauge.builder("notification.retry.queue.size", this::getRetryQueueSize)
                .description("Current size of retry queue")
                .register(meterRegistry);

        deadLetterQueueSize = Gauge.builder("notification.dead.letter.queue.size", this::getDeadLetterQueueSize)
                .description("Current size of dead letter queue")
                .register(meterRegistry);
    }

    public void recordNotificationSent() {
        notificationsSent.increment();
    }

    public void recordNotificationDelivered() {
        notificationsDelivered.increment();
    }

    public void recordNotificationFailed() {
        notificationsFailed.increment();
    }

    public void recordPushNotificationSent() {
        pushNotificationsSent.increment();
    }

    public void recordEmailNotificationSent() {
        emailNotificationsSent.increment();
    }

    public void recordSmsNotificationSent() {
        smsNotificationsSent.increment();
    }

    @Scheduled(fixedDelay = 60000) // Update every minute
    public void updateMetrics() {
        try {
            // Update delivery success rate
            double totalSent = notificationsSent.count();
            double totalDelivered = notificationsDelivered.count();

            if (totalSent > 0) {
                double successRate = totalDelivered / totalSent;
                meterRegistry.gauge("notification.delivery.success.rate", successRate);
            }

            // Update failure rate
            double totalFailed = notificationsFailed.count();
            if (totalSent > 0) {
                double failureRate = totalFailed / totalSent;
                meterRegistry.gauge("notification.delivery.failure.rate", failureRate);
            }

            log.debug("Updated notification metrics - Sent: {}, Delivered: {}, Failed: {}",
                    totalSent, totalDelivered, totalFailed);

        } catch (Exception e) {
            log.error("Failed to update notification metrics", e);
        }
    }

    private double getPendingNotificationsCount() {
        try {
            return (double) notificationRepository.countByStatus(NotificationStatus.PENDING);
        } catch (Exception e) {
            log.error("Failed to get pending notifications count", e);
            return 0.0;
        }
    }

    private double getRetryQueueSize() {
        // In a real implementation, you'd check Redis queue size
        // For now, return 0
        return 0;
    }

    private double getDeadLetterQueueSize() {
        // In a real implementation, you'd check Redis dead letter queue size
        // For now, return 0
        return 0;
    }
}