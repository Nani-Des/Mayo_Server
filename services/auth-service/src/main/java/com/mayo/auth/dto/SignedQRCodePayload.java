package com.mayo.auth.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Signed QR code payload
 */
@Data
@Builder
public class SignedQRCodePayload {
    private QRCodePayload payload;
    private String signature;
}