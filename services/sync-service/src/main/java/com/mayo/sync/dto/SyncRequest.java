package com.mayo.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncRequest {

    @NotBlank
    private String userId;

    @NotBlank
    private String deviceId;

    @NotNull
    private List<DeltaChangeDto> changes;

    @NotNull
    private Long lastSyncVersion;
}