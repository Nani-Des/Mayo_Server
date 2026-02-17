package com.mayo.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for creating a Hospital Admin
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateHospitalAdminRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotNull(message = "Hospital ID is required")
    private UUID hospitalId;

    // Optional: Initial password (if not provided, a random one will be generated)
    private String initialPassword;
}
