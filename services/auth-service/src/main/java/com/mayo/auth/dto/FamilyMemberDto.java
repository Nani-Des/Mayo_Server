package com.mayo.auth.dto;

import com.mayo.auth.entity.FamilyMember;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for FamilyMember information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyMemberDto {

    private UUID id;
    private UUID familyId;
    private UUID userId;
    private UUID patientId;
    private FamilyMember.FamilyRole role;
    private FamilyMember.MemberStatus status;
    private LocalDateTime joinedAt;
    private UUID invitedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Enriched fields
    private String firstName;
    private String lastName;
    private String relationship;
    private String dateOfBirth;
    private Integer age;
}