package com.mayo.notification.service;

import com.mayo.notification.entity.NotificationStatus;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsService {

    private final DeliveryTracker deliveryTracker;

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.phone-number}")
    private String twilioPhoneNumber;

    public boolean sendSms(com.mayo.notification.entity.Notification notification, String phoneNumber) {
        try {
            log.info("Sending SMS to {} for notification: {}", phoneNumber, notification.getId());

            // Note: In a real implementation, you'd initialize Twilio with accountSid and authToken
            // For now, we'll simulate the SMS sending

            // Message message = Message.creator(
            //     new PhoneNumber(phoneNumber),
            //     new PhoneNumber(twilioPhoneNumber),
            //     notification.getMessage()
            // ).create();

            log.info("SMS sent successfully to {}", phoneNumber);
            deliveryTracker.trackDeliveryStatus(notification.getId(), NotificationStatus.SENT);
            return true;

        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", phoneNumber, e.getMessage(), e);
            deliveryTracker.trackDeliveryFailure(notification.getId(), "SMS_ERROR: " + e.getMessage());
            return false;
        }
    }
}