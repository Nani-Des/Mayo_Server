package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Device API Key DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceApiKeyDto {

    private UUID id;
    private String apiKey;
    private String deviceId;
    private UUID hospitalId;
    private String name;
    private String description;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}