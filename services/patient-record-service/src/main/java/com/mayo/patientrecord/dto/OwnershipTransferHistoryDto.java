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
public class OwnershipTransferHistoryDto {

    private UUID transferId;
    private UUID previousOwnerId;
    private UUID newOwnerId;
    private LocalDateTime transferredAt;
    private String reason;
    private String status;
}