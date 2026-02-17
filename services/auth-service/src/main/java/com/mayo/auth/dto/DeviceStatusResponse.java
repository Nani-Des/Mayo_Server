package com.mayo.auth.dto;

import com.mayo.common.core.enums.DeviceStatus;
import com.mayo.common.core.enums.DeviceType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Device status response DTO
 */
@Data
@Builder
public class DeviceStatusResponse {
    private String deviceId;
    private DeviceStatus status;
    private DeviceType deviceType;
    private LocalDateTime registeredAt;
    private LocalDateTime lastCertificateUpdate;
    private CertificateStatus certificateStatus;
}