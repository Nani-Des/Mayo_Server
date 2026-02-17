package com.mayo.common.security.authorization;

import com.mayo.common.core.enums.Permission;
import com.mayo.common.core.enums.UserType;
import com.mayo.common.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Authorization service for checking permissions
 */
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Check if the request has the required permission
     */
    public boolean hasPermission(String authorizationHeader, Permission permission) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }

        String token = authorizationHeader.substring(7);
        return jwtTokenProvider.hasPermission(token, permission);
    }

    /**
     * Check if the request has any of the required permissions
     */
    public boolean hasAnyPermission(String authorizationHeader, Permission... permissions) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }

        String token = authorizationHeader.substring(7);
        return jwtTokenProvider.hasAnyPermission(token, permissions);
    }

    /**
     * Check if the user is of a specific type
     */
    public boolean hasUserType(String authorizationHeader, UserType userType) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }

        String token = authorizationHeader.substring(7);
        return jwtTokenProvider.getUserTypeFromToken(token) == userType;
    }

    /**
     * Get user ID from authorization header
     */
    public String getUserId(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authorizationHeader.substring(7);
        return jwtTokenProvider.getUserIdFromToken(token).toString();
    }

    /**
     * Check if user can access their own resource
     */
    public boolean canAccessOwnResource(String authorizationHeader, String resourceUserId) {
        String userId = getUserId(authorizationHeader);
        return userId != null && userId.equals(resourceUserId);
    }

    /**
     * Check if user is healthcare provider
     */
    public boolean isHealthcareProvider(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }

        String token = authorizationHeader.substring(7);
        UserType userType = jwtTokenProvider.getUserTypeFromToken(token);
        return userType.isHealthcareProvider();
    }

    /**
     * Check if user is hospital staff
     */
    public boolean isHospitalStaff(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }

        String token = authorizationHeader.substring(7);
        UserType userType = jwtTokenProvider.getUserTypeFromToken(token);
        return userType.isHospitalStaff();
    }
}