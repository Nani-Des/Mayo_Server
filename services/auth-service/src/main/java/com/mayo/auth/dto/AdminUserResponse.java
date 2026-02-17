package com.mayo.auth.dto;

import com.mayo.common.core.enums.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for admin user operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
    private UUID id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private UserType userType;
    private UUID hospitalId;
    private Boolean isActive;
    private Boolean emailVerified;
    private String accessToken; // For initial login response
}
