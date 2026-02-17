package com.mayo.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * QR code payload data
 */
@Data
@Builder
public class QRCodePayload {
    private String version;
    private String deviceId;
    private String certificate;
    private String nonce;
    private Instant timestamp;
    private Instant expiry;
    private QROperation operation;
}