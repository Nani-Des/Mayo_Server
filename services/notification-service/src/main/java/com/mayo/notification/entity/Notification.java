package com.mayo.notification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String type; // APPOINTMENT, MEDICATION, SYNC_COMPLETE, etc.

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column
    private String templateId;

    @ElementCollection
    @CollectionTable(name = "notification_template_data", joinColumns = @JoinColumn(name = "notification_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> templateData;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private NotificationPriority priority;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    @Column
    private Instant scheduledAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant sentAt;

    @Column
    private Instant deliveredAt;

    @Column
    private String failureReason;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (status == null) {
            status = NotificationStatus.PENDING;
        }
    }
}