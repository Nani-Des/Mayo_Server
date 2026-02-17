package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalStatsResponse {
    private UUID hospitalId;
    private long totalUsers;
    private long totalDoctors;
    private long totalNurses;
    private long totalReceptionists;
    private long totalPatients;
}
