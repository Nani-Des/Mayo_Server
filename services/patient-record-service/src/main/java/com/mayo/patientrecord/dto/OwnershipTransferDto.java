package com.mayo.patientrecord.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnershipTransferDto {

    private UUID id;
    private UUID patientId;
    private UUID previousOwnerId;
    private UUID newOwnerId;
    private String status;
    private String reason;
    private UUID initiatedBy;
    private UUID confirmedBy;
    private LocalDateTime initiatedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}