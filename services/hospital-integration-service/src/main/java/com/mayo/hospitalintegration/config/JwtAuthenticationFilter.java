package com.mayo.hospitalintegration.config;

import com.mayo.common.core.exception.UnauthorizedException;
import com.mayo.common.security.authorization.AuthorizationService;
import com.mayo.common.security.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.mayo.common.core.enums.UserType;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JWT Authentication Filter for validating JWT tokens or X-User headers from gateway
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthorizationService authorizationService;

    // Headers set by gateway
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_TYPE = "X-User-Type";
    private static final String HEADER_USER_PERMISSIONS = "X-User-Permissions";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // First, check if X-User headers are present (request came through gateway)
        String userId = request.getHeader(HEADER_USER_ID);
        String userType = request.getHeader(HEADER_USER_TYPE);
        String permissionsStr = request.getHeader(HEADER_USER_PERMISSIONS);

        if (userId != null && userType != null) {
            // Request came through gateway with user headers
            authenticateFromGatewayHeaders(request, userId, userType, permissionsStr);
        } else {
            // Check for JWT token in Authorization header (direct access or non-gateway request)
            authenticateFromJwt(request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateFromGatewayHeaders(HttpServletRequest request, String userId, String userType, String permissionsStr) {
        try {
            // Create authorities from user type and permissions
            List<SimpleGrantedAuthority> authorities = createAuthorities(userType, permissionsStr);

            // Create authentication token
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);

            // Add user info to request attributes for controllers
            request.setAttribute("userId", userId);
            request.setAttribute("userType", userType);
            request.setAttribute("userPermissions", permissionsStr);

            log.debug("Authenticated user from gateway headers: {} (type: {})", userId, userType);
        } catch (Exception e) {
            log.error("Failed to authenticate from gateway headers", e);
            throw UnauthorizedException.invalidToken();
        }
    }

    private void authenticateFromJwt(HttpServletRequest request) {
        final String requestTokenHeader = request.getHeader("Authorization");
        String username = null;
        String jwtToken = null;

        // JWT Token is in the form "Bearer token". Remove Bearer word and get only the Token
        if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
            jwtToken = requestTokenHeader.substring(7);
            try {
                if (jwtTokenProvider.validateToken(jwtToken)) {
                    UUID userId = jwtTokenProvider.getUserIdFromToken(jwtToken);
                    username = userId.toString();

                    UserType userType = jwtTokenProvider.getUserTypeFromToken(jwtToken);
                    String userTypeStr = userType != null ? userType.name() : null;
                    String permissions = jwtTokenProvider.getPermissionsFromToken(jwtToken)
                            .stream().map(Enum::name).collect(Collectors.joining(","));

                    // Create authorities from JWT claims
                    List<SimpleGrantedAuthority> authorities = createAuthorities(userTypeStr, permissions);

                    // Set authentication in context
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);

                    // Add user info to request attributes for controllers
                    request.setAttribute("userId", userId.toString());
                    request.setAttribute("userType", userTypeStr);
                    request.setAttribute("userPermissions", permissions);
                    request.setAttribute("deviceId", jwtTokenProvider.getDeviceIdFromToken(jwtToken));

                    log.debug("Authenticated user from JWT: {} (type: {})", userId, userType);
                } else {
                    log.warn("Invalid JWT token for request: {}", request.getRequestURI());
                    throw UnauthorizedException.invalidToken();
                }
            } catch (Exception e) {
                log.error("Unable to get JWT Token or JWT Token has expired", e);
                throw UnauthorizedException.invalidToken();
            }
        } else {
            log.debug("JWT Token does not begin with Bearer String or is null");
        }
    }

    private List<SimpleGrantedAuthority> createAuthorities(String userType, String permissionsStr) {
        // Add user type as a role (ROLE_ prefix for Spring Security)
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        if (userType != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + userType));
        }

        // Add individual permissions as authorities
        if (permissionsStr != null && !permissionsStr.isEmpty()) {
            List<String> permissions = Arrays.stream(permissionsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            authorities.addAll(permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList()));
        }

        return authorities;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip filtering for actuator and swagger endpoints
        return path.startsWith("/actuator/") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs/");
    }
}
