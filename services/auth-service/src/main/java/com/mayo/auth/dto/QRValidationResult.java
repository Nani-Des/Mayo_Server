package com.mayo.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * QR code validation result
 */
@Data
@Builder
public class QRValidationResult {
    private boolean valid;
    private String deviceId;
    private QROperation operation;
    private String certificate;
    private String error;
}