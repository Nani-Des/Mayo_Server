package com.mayo.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for delta sync operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeltaSyncResponse {

    private List<DeltaChangeDto> changes;
    private List<VersionSummary> latestVersions;
    private Long totalChanges;
    private Integer processedChanges;
    private Long compressedSize;
    private Long newVersion;
    private LocalDateTime syncTimestamp;
    private String status = "SUCCESS";
    private String message;
}