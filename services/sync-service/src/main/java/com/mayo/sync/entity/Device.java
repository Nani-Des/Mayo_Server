package com.mayo.sync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "device_id", unique = true, nullable = false)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false)
    private DeviceType deviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "pairing_method", nullable = false)
    private PairingMethod pairingMethod;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "certificate", columnDefinition = "TEXT")
    private String certificate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeviceStatus status;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(name = "paired_at", nullable = false)
    private LocalDateTime pairedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "hospital_id")
    private UUID hospitalId;

    @Version
    @Column(name = "version")
    private Integer version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (pairedAt == null) {
            pairedAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum PairingMethod {
        BLUETOOTH,
        WIFI,
        USB
    }

    public enum DeviceType {
        MOBILE,
        DESKTOP,
        TABLET,
        WEARABLE
    }

    public enum DeviceStatus {
        PAIRED,
        ACTIVE,
        INACTIVE,
        BLOCKED
    }
}