package com.mayo.notification.service;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.TokenUtil;
import com.mayo.notification.entity.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApnsService {

    private final DeliveryTracker deliveryTracker;

    @Value("${apns.key-id}")
    private String keyId;

    @Value("${apns.team-id}")
    private String teamId;

    @Value("${apns.bundle-id}")
    private String bundleId;

    @Value("${apns.private-key:classpath:apns-private-key.p8}")
    private String privateKeyPath;

    private ApnsClient apnsClient;

    public boolean sendPushNotification(com.mayo.notification.entity.Notification notification, String deviceToken) {
        try {
            log.info("Sending APNs notification to token: {} for notification: {}", maskToken(deviceToken),
                    notification.getId());

            ApnsClient client = getApnsClient();

            SimpleApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
            payloadBuilder.setAlertTitle(notification.getTitle());
            payloadBuilder.setAlertBody(notification.getMessage());
            payloadBuilder.addCustomProperty("notificationId", notification.getId().toString());
            payloadBuilder.addCustomProperty("type", notification.getType());
            payloadBuilder.addCustomProperty("priority", notification.getPriority().name());

            String payload = payloadBuilder.build();

            SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(
                    TokenUtil.sanitizeTokenString(deviceToken),
                    bundleId,
                    payload,
                    Instant.now().plusSeconds(300), // 5 minutes expiration
                    com.eatthepath.pushy.apns.DeliveryPriority.IMMEDIATE);

            PushNotificationResponse<SimpleApnsPushNotification> response = client.sendNotification(pushNotification)
                    .get();

            if (response.isAccepted()) {
                log.info("APNs message sent successfully");
                deliveryTracker.trackDeliveryStatus(notification.getId(), NotificationStatus.SENT);
                return true;
            } else {
                log.error("APNs message rejected: {}", response.getRejectionReason());
                String failureReason = "APNS_REJECTED: " + response.getRejectionReason();
                deliveryTracker.trackDeliveryFailure(notification.getId(), failureReason);

                // Check if token is invalid
                if ("BadDeviceToken".equals(response.getRejectionReason()) ||
                        "Unregistered".equals(response.getRejectionReason())) {
                    log.warn("Invalid APNs token detected: {}", maskToken(deviceToken));
                }

                return false;
            }

        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to send APNs notification: {}", e.getMessage(), e);
            deliveryTracker.trackDeliveryFailure(notification.getId(), "APNS_ERROR: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.error("Unexpected error sending APNs notification", e);
            deliveryTracker.trackDeliveryFailure(notification.getId(), "UNEXPECTED_ERROR: " + e.getMessage());
            return false;
        }
    }

    private synchronized ApnsClient getApnsClient() throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        if (apnsClient == null) {
            File privateKeyFile = new File(privateKeyPath.replace("classpath:", "src/main/resources/"));
            if (!privateKeyFile.exists()) {
                throw new IOException("APNs private key file not found: " + privateKeyPath);
            }

            apnsClient = new ApnsClientBuilder()
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST) // or DEVELOPMENT_APNS_HOST for sandbox
                    .setSigningKey(ApnsSigningKey.loadFromPkcs8File(privateKeyFile, teamId, keyId))
                    .build();
        }
        return apnsClient;
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return token;
        }
        return token.substring(0, 10) + "...";
    }
}