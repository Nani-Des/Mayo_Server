package com.mayo.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Device Authorization Code entity for OAuth 2.0 Device Flow
 */
@Entity
@Table(name = "device_authorization_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceAuthorizationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "device_code", nullable = false, unique = true)
    private String deviceCode;

    @Column(name = "user_code", nullable = false, unique = true)
    private String userCode;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "hospital_id", nullable = false)
    private UUID hospitalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_polled_at")
    private LocalDateTime lastPolledAt;

    @Column(name = "interval_seconds", nullable = false)
    private Integer intervalSeconds;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum Status {
        PENDING,
        APPROVED,
        DENIED,
        EXPIRED
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isPending() {
        return status == Status.PENDING && !isExpired();
    }
}