package com.mayo.auth.dto;

import com.mayo.common.core.enums.UserType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class StaffResponse {
    private UUID id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private UserType userType;
    private UUID hospitalId;
    private Boolean isActive;
    private Boolean emailVerified;
    private String ghanaCardId;
}
