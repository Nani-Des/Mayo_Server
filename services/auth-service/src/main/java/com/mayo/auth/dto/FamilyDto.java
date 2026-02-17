package com.mayo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for Family information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyDto {

    private UUID id;
    private String name;
    private UUID createdBy;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}