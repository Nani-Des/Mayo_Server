package com.mayo.notification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "user_notification_preferences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String notificationType;

    @Column(nullable = false)
    private boolean pushEnabled = true;

    @Column(nullable = false)
    private boolean emailEnabled = false;

    @Column(nullable = false)
    private boolean smsEnabled = false;

    @Column
    private String emailAddress;

    @Column
    private String phoneNumber;

    @Column
    private String fcmToken;

    @Column
    private String apnsToken;

    @Column
    private String timezone;

    @Column
    private String language = "en";

    @Column(nullable = false)
    private boolean quietHoursEnabled = false;

    @Column
    private LocalTime quietHoursStart;

    @Column
    private LocalTime quietHoursEnd;
}