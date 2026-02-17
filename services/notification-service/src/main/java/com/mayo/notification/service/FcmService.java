package com.mayo.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.mayo.notification.entity.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {

    private final FirebaseMessaging firebaseMessaging;
    private final DeliveryTracker deliveryTracker;

    public boolean sendPushNotification(com.mayo.notification.entity.Notification notification, String fcmToken) {
        try {
            log.info("Sending FCM notification to token: {} for notification: {}", maskToken(fcmToken), notification.getId());

            Notification fcmNotification = Notification.builder()
                    .setTitle(notification.getTitle())
                    .setBody(notification.getMessage())
                    .build();

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(fcmNotification)
                    .putData("notificationId", notification.getId().toString())
                    .putData("type", notification.getType())
                    .putData("priority", notification.getPriority().name())
                    .build();

            String response = firebaseMessaging.send(message);

            log.info("FCM message sent successfully: {}", response);

            // Update delivery status
            deliveryTracker.trackDeliveryStatus(notification.getId(), NotificationStatus.SENT);

            return true;

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send FCM notification: {}", e.getMessage(), e);

            String failureReason = "FCM_ERROR: " + e.getMessage();
            deliveryTracker.trackDeliveryFailure(notification.getId(), failureReason);

            // Check if token is invalid
            if (e.getMessagingErrorCode() != null) {
                switch (e.getMessagingErrorCode()) {
                    case INVALID_ARGUMENT:
                    case UNREGISTERED:
                        // Token is invalid, should be removed from user preferences
                        log.warn("Invalid FCM token detected: {}", maskToken(fcmToken));
                        break;
                    default:
                        // Other errors might be retried
                        break;
                }
            }

            return false;
        } catch (Exception e) {
            log.error("Unexpected error sending FCM notification", e);
            deliveryTracker.trackDeliveryFailure(notification.getId(), "UNEXPECTED_ERROR: " + e.getMessage());
            return false;
        }
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return token;
        }
        return token.substring(0, 10) + "...";
    }
}