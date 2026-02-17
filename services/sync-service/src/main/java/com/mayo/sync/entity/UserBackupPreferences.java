package com.mayo.sync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_backup_preferences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBackupPreferences {

    @Id
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "encryption_key_hash")
    private String encryptionKeyHash;

    @Column(name = "last_backup_at")
    private LocalDateTime lastBackupAt;

    @Column(name = "retention_years", nullable = false)
    private Integer retentionYears;

    @Column(name = "backup_enabled", nullable = false)
    private Boolean backupEnabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}