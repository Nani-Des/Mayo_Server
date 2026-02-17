package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO for creating device API keys
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceApiKeyRequest {

    private String deviceId;
    private UUID hospitalId;
    private String name;
    private String description;
    private LocalDateTime expiresAt;
}