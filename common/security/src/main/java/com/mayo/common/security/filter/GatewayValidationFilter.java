package com.mayo.common.security.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filter that validates all incoming requests contain the gateway secret header
 * and extracts user information from X-User headers (set by the API gateway).
 * 
 * This ensures requests can only come through the API gateway and properly
 * populates Spring Security authentication from gateway headers.
 * 
 * Note: This filter is NOT annotated with @Component. It must be manually registered
 * via FilterRegistrationBean in each service's SecurityConfig.
 * 
 * Public endpoints (like /api/auth/login, /api/auth/register) bypass gateway validation
 * to allow direct access for testing and development.
 */
public class GatewayValidationFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(GatewayValidationFilter.class);
    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";
    
    // Headers set by gateway for user authentication
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_TYPE = "X-User-Type";
    private static final String HEADER_USER_PERMISSIONS = "X-User-Permissions";

    // Public endpoints that bypass gateway validation
    // These endpoints handle their own authentication
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
        "/api/auth/login",
        "/api/v1/auth/login",
        "/api/auth/register",
        "/api/v1/auth/register",
        "/api/auth/device-login",
        "/api/v1/auth/device-login",
        "/api/auth/refresh",
        "/api/v1/auth/refresh",
        "/api/auth/verify",
        "/api/v1/auth/verify",
        "/api/auth/forgot-password",
        "/api/v1/auth/forgot-password",
        "/api/auth/reset-password",
        "/api/v1/auth/reset-password",
        "/api/auth/health",
        "/api/v1/auth/health",
        "/api/v1/auth/devices/check-registration",
        "/api/auth/devices/check-registration",
        "/api/v1/devices/check-registration",
        "/api/devices/check-registration",
        "/api/v1/devices/public/check",
        "/api/devices/public/check"
    );

    private final String expectedSecret;

    public GatewayValidationFilter(String expectedSecret) {
        this.expectedSecret = expectedSecret;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestUri = httpRequest.getRequestURI();

        // Allow actuator endpoints to bypass validation (for health checks, monitoring)
        if (requestUri.startsWith("/actuator/")) {
            chain.doFilter(request, response);
            return;
        }

        // Allow public auth endpoints to bypass gateway validation
        // These endpoints handle their own authentication
        if (isPublicAuthEndpoint(requestUri)) {
            logger.info("BYPASSING gateway validation for public endpoint: {}", requestUri);
            chain.doFilter(request, response);
            return;
        }

        logger.info("Checking gateway secret for URI: {} - not a public endpoint", requestUri);

        // Validate gateway secret header
        String gatewaySecret = httpRequest.getHeader(GATEWAY_SECRET_HEADER);

        if (gatewaySecret == null || !gatewaySecret.equals(expectedSecret)) {
            logger.warn("Request rejected: missing or invalid gateway secret header. URI: {}, IP: {}",
                    requestUri, httpRequest.getRemoteAddr());
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                    "{\"error\":\"Forbidden\",\"message\":\"Direct access not allowed. Requests must go through API gateway.\"}"
            );
            return;
        }

        // Extract user information from gateway headers and set authentication
        setUserAuthentication(httpRequest);

        // Valid gateway secret and user authenticated - proceed with request
        chain.doFilter(request, response);
    }

    private boolean isPublicAuthEndpoint(String requestUri) {
        return PUBLIC_AUTH_PATHS.stream().anyMatch(requestUri::startsWith);
    }

    private void setUserAuthentication(HttpServletRequest request) {
        String userId = request.getHeader(HEADER_USER_ID);
        String userType = request.getHeader(HEADER_USER_TYPE);
        String permissionsStr = request.getHeader(HEADER_USER_PERMISSIONS);

        // If no user headers, let request proceed without authentication
        // (some endpoints might be public even behind the gateway)
        if (userId == null || userType == null) {
            logger.debug("No user headers found for URI: {}", request.getRequestURI());
            return;
        }

        try {
            // Create authorities from user type and permissions
            List<SimpleGrantedAuthority> authorities = createAuthorities(userType, permissionsStr);

            // Create authentication token
            UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            // Set authentication in security context
            SecurityContextHolder.getContext().setAuthentication(authentication);

            logger.debug("Authenticated user from gateway headers: {} (type: {})", userId, userType);
        } catch (Exception e) {
            logger.error("Failed to create authentication from gateway headers: {}", e.getMessage());
            // Don't fail the request, just log the error
        }
    }

    private List<SimpleGrantedAuthority> createAuthorities(String userType, String permissionsStr) {
        // Add user type as a role (ROLE_ prefix for Spring Security)
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + userType));

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
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("GatewayValidationFilter initialized - direct service access is blocked and user authentication is configured");
    }

    @Override
    public void destroy() {
        // Cleanup if needed
    }
}
