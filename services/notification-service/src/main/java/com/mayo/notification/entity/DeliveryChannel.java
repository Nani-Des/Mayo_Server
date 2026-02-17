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
@Table(name = "delivery_channels")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name; // FCM, APNs, EMAIL, SMS

    @Column(nullable = false)
    private String provider; // Firebase, Apple, SendGrid, Twilio

    @Column(nullable = false)
    private boolean active = true;

    @ElementCollection
    @CollectionTable(name = "delivery_channel_config",
                     joinColumns = @JoinColumn(name = "channel_id"))
    @MapKeyColumn(name = "config_key")
    @Column(name = "config_value")
    private Map<String, String> configuration; // API keys, endpoints, etc.

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}