package com.mayo.sync.dto;

import com.mayo.sync.entity.Device;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DevicePairRequest {

    @NotBlank
    private String deviceId;

    @NotNull
    private Device.PairingMethod pairingMethod;

    @NotBlank
    private String publicKey;

    private String certificate;
}