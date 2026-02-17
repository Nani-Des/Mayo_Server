package com.mayo.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeltaSyncRequest {

    @NotBlank
    private String userId;

    @NotBlank
    private String deviceId;

    @NotNull
    private Long lastSyncVersion;

    private String recordType;
}