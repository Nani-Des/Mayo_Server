package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for hospital summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HospitalSummaryResponse {
    private UUID id;
    private String name;
    private String code;
    private String address;
    private String phoneNumber;
    private Boolean isActive;
}
