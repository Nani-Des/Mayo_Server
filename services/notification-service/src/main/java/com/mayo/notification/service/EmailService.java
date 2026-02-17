package com.mayo.notification.service;

import com.mayo.notification.entity.NotificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final DeliveryTracker deliveryTracker;

    public boolean sendEmail(com.mayo.notification.entity.Notification notification, String emailAddress) {
        try {
            log.info("Sending email to {} for notification: {}", emailAddress, notification.getId());

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(emailAddress);
            message.setSubject(notification.getTitle());
            message.setText(notification.getMessage());

            mailSender.send(message);

            log.info("Email sent successfully to {}", emailAddress);
            deliveryTracker.trackDeliveryStatus(notification.getId(), NotificationStatus.SENT);
            return true;

        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", emailAddress, e.getMessage(), e);
            deliveryTracker.trackDeliveryFailure(notification.getId(), "EMAIL_ERROR: " + e.getMessage());
            return false;
        }
    }
}