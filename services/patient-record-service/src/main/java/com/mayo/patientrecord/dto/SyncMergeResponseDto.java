package com.mayo.patientrecord.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncMergeResponseDto {
    private UUID patientId;
    private UUID visitId;
    private int medicationsSynced;
    private int labsSynced;
    private int historySynced;
    private String status;
}
