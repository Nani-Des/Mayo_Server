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
public class ConflictDto {

    private UUID id;

    @NotBlank
    private String recordId;

    @NotBlank
    private String recordType;

    @NotNull
    private Long localVersion;

    @NotNull
    private Long serverVersion;

    @NotNull
    private ConflictType conflictType;

    @NotNull
    private ResolutionStatus resolutionStatus;

    private String localData;
    private String serverData;

    public enum ConflictType {
        VERSION_CONFLICT,
        DATA_CONFLICT,
        DELETION_CONFLICT
    }

    public enum ResolutionStatus {
        PENDING,
        RESOLVED_LOCAL_WINS,
        RESOLVED_SERVER_WINS,
        RESOLVED_MERGED,
        RESOLVED_MANUAL
    }
}