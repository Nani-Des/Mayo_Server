package com.mayo.notification.service;

import com.mayo.notification.entity.Notification;
import com.mayo.notification.entity.NotificationStatus;
import com.mayo.notification.entity.UserNotificationPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryRouter {

    private final FcmService fcmService;
    private final ApnsService apnsService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final DeliveryTracker deliveryTracker;

    @Async
    public void routeNotification(Notification notification, List<UserNotificationPreferences> preferences) {
        try {
            log.info("Routing notification {} to delivery channels", notification.getId());

            // Update status to sending
            deliveryTracker.trackDeliveryStatus(notification.getId(), NotificationStatus.SENDING);

            boolean delivered = false;

            // Try push notifications first (FCM and APNs)
            delivered = deliverPushNotifications(notification, preferences) || delivered;

            // Then try email if enabled
            delivered = deliverEmail(notification, preferences) || delivered;

            // Finally try SMS if enabled
            delivered = deliverSms(notification, preferences) || delivered;

            if (delivered) {
                deliveryTracker.trackDeliveryStatus(notification.getId(), NotificationStatus.DELIVERED);
            } else {
                deliveryTracker.trackDeliveryFailure(notification.getId(), "NO_SUCCESSFUL_DELIVERY_CHANNEL");
            }

        } catch (Exception e) {
            log.error("Failed to route notification: {}", notification.getId(), e);
            deliveryTracker.trackDeliveryFailure(notification.getId(), "ROUTING_ERROR: " + e.getMessage());
        }
    }

    private boolean deliverPushNotifications(Notification notification, List<UserNotificationPreferences> preferences) {
        boolean delivered = false;

        for (UserNotificationPreferences pref : preferences) {
            // Check if push is enabled for this notification type
            if (isPushEnabled(pref, notification.getType())) {
                // Try FCM
                if (pref.getFcmToken() != null && !pref.getFcmToken().trim().isEmpty()) {
                    if (fcmService.sendPushNotification(notification, pref.getFcmToken())) {
                        delivered = true;
                    }
                }

                // Try APNs
                if (pref.getApnsToken() != null && !pref.getApnsToken().trim().isEmpty()) {
                    if (apnsService.sendPushNotification(notification, pref.getApnsToken())) {
                        delivered = true;
                    }
                }
            }
        }

        return delivered;
    }

    private boolean deliverEmail(Notification notification, List<UserNotificationPreferences> preferences) {
        for (UserNotificationPreferences pref : preferences) {
            if (isEmailEnabled(pref, notification.getType()) &&
                pref.getEmailAddress() != null && !pref.getEmailAddress().trim().isEmpty()) {

                return emailService.sendEmail(notification, pref.getEmailAddress());
            }
        }
        return false;
    }

    private boolean deliverSms(Notification notification, List<UserNotificationPreferences> preferences) {
        for (UserNotificationPreferences pref : preferences) {
            if (isSmsEnabled(pref, notification.getType()) &&
                pref.getPhoneNumber() != null && !pref.getPhoneNumber().trim().isEmpty()) {

                return smsService.sendSms(notification, pref.getPhoneNumber());
            }
        }
        return false;
    }

    private boolean isPushEnabled(UserNotificationPreferences pref, String notificationType) {
        return pref.isPushEnabled() &&
               (pref.getNotificationType().equals(notificationType) ||
                pref.getNotificationType().equals("ALL"));
    }

    private boolean isEmailEnabled(UserNotificationPreferences pref, String notificationType) {
        return pref.isEmailEnabled() &&
               (pref.getNotificationType().equals(notificationType) ||
                pref.getNotificationType().equals("ALL"));
    }

    private boolean isSmsEnabled(UserNotificationPreferences pref, String notificationType) {
        return pref.isSmsEnabled() &&
               (pref.getNotificationType().equals(notificationType) ||
                pref.getNotificationType().equals("ALL"));
    }
}