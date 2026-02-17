package com.mayo.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Summary DTO for version information in P2P sync
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VersionSummary {
    private String recordType;
    private String recordId;
    private Long version;
    private String contentHash;
    private LocalDateTime timestamp;
    private UUID doctorUserId;
}