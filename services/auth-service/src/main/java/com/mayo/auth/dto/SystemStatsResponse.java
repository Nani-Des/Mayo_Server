package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for system statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatsResponse {
    private long totalUsers;
    private long totalHospitals;
    private long totalDoctors;
    private long totalNurses;
    private long totalPatients;
    private long totalAdmins;
}
