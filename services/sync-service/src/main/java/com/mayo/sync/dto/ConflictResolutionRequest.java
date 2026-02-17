package com.mayo.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConflictResolutionRequest {

    @NotNull
    private UUID conflictId;

    @NotNull
    private ConflictDto.ResolutionStatus resolution;

    private String resolvedData;
}