package com.mayo.common.security.jwt;

import com.mayo.common.core.exception.UnauthorizedException;
import com.mayo.common.core.enums.Permission;
import com.mayo.common.core.enums.UserType;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * JWT Token Provider for authentication
 * Generates and validates JWT tokens
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiry:900000}") // 15 minutes default
    private long accessTokenExpiry;

    @Value("${jwt.refresh-token-expiry:604800000}") // 7 days default
    private long refreshTokenExpiry;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generate access token
     */
    public String generateAccessToken(UUID userId, String email, UserType userType, Set<Permission> permissions, UUID hospitalId) {
        return generateAccessToken(userId, email, userType, permissions, hospitalId, false, null);
    }

    /**
     * Generate access token with RBAC support
     */
    public String generateAccessToken(UUID userId, String email, UserType userType, Set<Permission> permissions, UUID hospitalId, boolean isSuperAdmin, UUID roleId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("email", email);
        claims.put("userType", userType.name());
        claims.put("permissions", permissions.stream().map(Permission::name).toList());
        if (hospitalId != null) {
            claims.put("hospitalId", hospitalId.toString());
        }
        claims.put("isSuperAdmin", isSuperAdmin);
        if (roleId != null) {
            claims.put("roleId", roleId.toString());
        }
        claims.put("tokenType", "ACCESS");

        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessTokenExpiry);

        return Jwts.builder()
                .claims(claims)
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Generate refresh token
     */
    public String generateRefreshToken(UUID userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("tokenType", "REFRESH");

        Instant now = Instant.now();
        Instant expiry = now.plusMillis(refreshTokenExpiry);

        return Jwts.builder()
                .claims(claims)
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Generate device-specific access token
     */
    public String generateDeviceAccessToken(UUID userId, String email, UserType userType, Set<Permission> permissions,
            String deviceId, UUID hospitalId) {
        return generateDeviceAccessToken(userId, email, userType, permissions, deviceId, hospitalId, false, null);
    }

    /**
     * Generate device-specific access token with RBAC support
     */
    public String generateDeviceAccessToken(UUID userId, String email, UserType userType, Set<Permission> permissions,
            String deviceId, UUID hospitalId, boolean isSuperAdmin, UUID roleId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("email", email);
        claims.put("userType", userType.name());
        claims.put("permissions", permissions.stream().map(Permission::name).toList());
        claims.put("deviceId", deviceId);
        if (hospitalId != null) {
            claims.put("hospitalId", hospitalId.toString());
        }
        claims.put("isSuperAdmin", isSuperAdmin);
        if (roleId != null) {
            claims.put("roleId", roleId.toString());
        }
        claims.put("tokenType", "DEVICE_ACCESS");

        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessTokenExpiry);

        return Jwts.builder()
                .claims(claims)
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Generate device-specific refresh token
     */
    public String generateDeviceRefreshToken(UUID userId, String deviceId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("deviceId", deviceId);
        claims.put("tokenType", "DEVICE_REFRESH");

        Instant now = Instant.now();
        Instant expiry = now.plusMillis(refreshTokenExpiry);

        return Jwts.builder()
                .claims(claims)
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Validate token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }

    /**
     * Get user ID from token
     */
    public UUID getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        String userIdStr = claims.get("userId", String.class);
        return UUID.fromString(userIdStr);
    }

    /**
     * Get email from token
     */
    public String getEmailFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("email", String.class);
    }

    /**
     * Get user type from token
     */
    public UserType getUserTypeFromToken(String token) {
        Claims claims = getClaims(token);
        String userTypeStr = claims.get("userType", String.class);
        return UserType.valueOf(userTypeStr);
    }

    /**
     * Check if token is access token
     */
    public boolean isAccessToken(String token) {
        Claims claims = getClaims(token);
        String tokenType = claims.get("tokenType", String.class);
        return "ACCESS".equals(tokenType);
    }

    /**
     * Check if token is refresh token
     */
    public boolean isRefreshToken(String token) {
        Claims claims = getClaims(token);
        String tokenType = claims.get("tokenType", String.class);
        return "REFRESH".equals(tokenType);
    }

    /**
     * Check if token is device access token
     */
    public boolean isDeviceAccessToken(String token) {
        Claims claims = getClaims(token);
        String tokenType = claims.get("tokenType", String.class);
        return "DEVICE_ACCESS".equals(tokenType);
    }

    /**
     * Check if token is device refresh token
     */
    public boolean isDeviceRefreshToken(String token) {
        Claims claims = getClaims(token);
        String tokenType = claims.get("tokenType", String.class);
        return "DEVICE_REFRESH".equals(tokenType);
    }

    /**
     * Get device ID from token
     */
    public String getDeviceIdFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("deviceId", String.class);
    }

    /**
     * Get permissions from token
     */
    @SuppressWarnings("unchecked")
    public Set<Permission> getPermissionsFromToken(String token) {
        Claims claims = getClaims(token);
        List<String> permissionStrings = (List<String>) claims.get("permissions");
        if (permissionStrings == null) {
            return Set.of();
        }
        return permissionStrings.stream()
                .map(Permission::valueOf)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Check if token has specific permission
     */
    public boolean hasPermission(String token, Permission permission) {
        Set<Permission> permissions = getPermissionsFromToken(token);
        return permissions.contains(permission);
    }

    /**
     * Check if token has any of the specified permissions
     */
    public boolean hasAnyPermission(String token, Permission... permissions) {
        Set<Permission> userPermissions = getPermissionsFromToken(token);
        return java.util.Arrays.stream(permissions)
                .anyMatch(userPermissions::contains);
    }

    /**
     * Get token expiration date
     */
    public Date getExpirationFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.getExpiration();
    }

    /**
     * Get all claims from token
     */
    private Claims getClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw UnauthorizedException.invalidToken();
        } catch (JwtException ex) {
            throw UnauthorizedException.invalidToken();
        }
    }

    /**
     * Get hospital ID from token
     */
    public UUID getHospitalIdFromToken(String token) {
        Claims claims = getClaims(token);
        String hospitalIdStr = claims.get("hospitalId", String.class);
        return hospitalIdStr != null ? UUID.fromString(hospitalIdStr) : null;
    }

    /**
     * Check if user is super admin from token
     */
    public boolean isSuperAdminFromToken(String token) {
        Claims claims = getClaims(token);
        Boolean isSuperAdmin = claims.get("isSuperAdmin", Boolean.class);
        return isSuperAdmin != null && isSuperAdmin;
    }

    /**
     * Get role ID from token
     */
    public UUID getRoleIdFromToken(String token) {
        Claims claims = getClaims(token);
        String roleIdStr = claims.get("roleId", String.class);
        return roleIdStr != null ? UUID.fromString(roleIdStr) : null;
    }

    /**
     * Check if user is admin (any level) from token
     */
    public boolean isAdminFromToken(String token) {
        UserType userType = getUserTypeFromToken(token);
        return userType.isAdmin() || isSuperAdminFromToken(token);
    }

    /**
     * Check if user is hospital admin from token
     */
    public boolean isHospitalAdminFromToken(String token) {
        UserType userType = getUserTypeFromToken(token);
        return userType.isHospitalAdmin();
    }
}
