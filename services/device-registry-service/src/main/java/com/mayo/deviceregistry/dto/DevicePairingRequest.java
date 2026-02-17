package com.mayo.deviceregistry.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * Device pairing request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevicePairingRequest {

    @NotBlank
    private String pairingCode;
}