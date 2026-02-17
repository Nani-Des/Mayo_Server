package com.mayo.auth.dto;

import com.mayo.common.core.enums.DeviceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Certificate status information
 */
@Data
@Builder
public class CertificateStatus {
    private String deviceId;
    private DeviceStatus status;
    private boolean certificateValid;
    private LocalDateTime lastUpdate;
    private String error;
}