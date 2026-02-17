package com.mayo.auth.dto;

import com.mayo.auth.entity.FamilyMember;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request DTO for adding a member to a family
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddFamilyMemberRequest {

    private UUID userId; // Optional for new dependents

    private UUID patientId; // Optional

    private String firstName;
    private String lastName;
    private String relationship;
    // format: yyyy-MM-dd
    private String dateOfBirth;

    @NotNull(message = "Role is required")
    private FamilyMember.FamilyRole role;
}