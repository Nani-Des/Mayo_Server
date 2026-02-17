package com.mayo.hospitalintegration.service;

import com.mayo.hospitalintegration.entity.DataTransferSession;
import com.mayo.hospitalintegration.entity.Hospital;
import com.mayo.hospitalintegration.entity.HospitalDevice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for authenticating and authorizing data transfer operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferAuthenticationService {

    private final AccessVerificationService accessVerificationService;

    /**
     * Authenticates a transfer request
     * 
     * @param session     Transfer session
     * @param credentials Authentication credentials
     * @return AuthenticationResult
     */
    public AuthenticationResult authenticateTransfer(DataTransferSession session,
            Map<String, Object> credentials) {
        try {
            log.info("Authenticating transfer for session: {}", session.getSessionId());

            // Validate session
            if (session.getHospital() == null || session.getDevice() == null) {
                return AuthenticationResult.failure("Invalid session: missing hospital or device");
            }

            // Check device access permissions
            boolean hasAccess = accessVerificationService.verifyDeviceAccess(
                    session.getDevice().getDeviceId(),
                    session.getHospital().getHospitalId(),
                    session.getDataType().name());

            if (!hasAccess) {
                log.warn("Access denied for device {} to hospital {} data type {}",
                        session.getDevice().getDeviceName(),
                        session.getHospital().getName(),
                        session.getDataType());
                return AuthenticationResult.failure("Access denied");
            }

            // Validate protocol-specific authentication
            AuthenticationResult protocolAuth = authenticateProtocol(session, credentials);
            if (!protocolAuth.isAuthenticated()) {
                return protocolAuth;
            }

            log.info("Transfer authentication successful for session: {}", session.getSessionId());
            return AuthenticationResult.success();

        } catch (Exception e) {
            log.error("Transfer authentication failed for session: {}", session.getSessionId(), e);
            return AuthenticationResult.failure("Authentication error: " + e.getMessage());
        }
    }

    /**
     * Authorizes a transfer operation based on policies
     * 
     * @param session   Transfer session
     * @param operation Operation to authorize
     * @return AuthorizationResult
     */
    public AuthorizationResult authorizeTransfer(DataTransferSession session, String operation) {
        try {
            log.info("Authorizing transfer operation {} for session: {}", operation, session.getSessionId());

            // Check transfer type permissions
            boolean transferAllowed = checkTransferTypePermission(session, operation);
            if (!transferAllowed) {
                return AuthorizationResult.denied("Transfer type not allowed");
            }

            // Check data type permissions
            boolean dataAllowed = checkDataTypePermission(session);
            if (!dataAllowed) {
                return AuthorizationResult.denied("Data type access not allowed");
            }

            // Check protocol permissions
            boolean protocolAllowed = checkProtocolPermission(session);
            if (!protocolAllowed) {
                return AuthorizationResult.denied("Protocol not allowed");
            }

            // Check rate limits and quotas
            boolean withinLimits = checkRateLimits(session);
            if (!withinLimits) {
                return AuthorizationResult.denied("Rate limit exceeded");
            }

            log.info("Transfer authorization successful for session: {}", session.getSessionId());
            return AuthorizationResult.allowed();

        } catch (Exception e) {
            log.error("Transfer authorization failed for session: {}", session.getSessionId(), e);
            return AuthorizationResult.denied("Authorization error: " + e.getMessage());
        }
    }

    /**
     * Validates transfer session integrity
     * 
     * @param session Transfer session
     * @return true if valid
     */
    public boolean validateSession(DataTransferSession session) {
        if (session == null) {
            return false;
        }

        // Check required fields
        if (session.getSessionId() == null || session.getSessionId().isEmpty()) {
            return false;
        }

        if (session.getHospital() == null || session.getDevice() == null) {
            return false;
        }

        if (session.getTransferType() == null || session.getDataType() == null ||
                session.getProtocol() == null) {
            return false;
        }

        // Check session status
        if (session.getStatus() == DataTransferSession.TransferStatus.CANCELLED ||
                session.getStatus() == DataTransferSession.TransferStatus.TIMEOUT) {
            return false;
        }

        return true;
    }

    private AuthenticationResult authenticateProtocol(DataTransferSession session,
            Map<String, Object> credentials) {
        switch (session.getProtocol()) {
            case HL7:
                return authenticateHL7(session, credentials);
            case FHIR:
                return authenticateFHIR(session, credentials);
            case DICOM:
                return authenticateDICOM(session, credentials);
            case REST_API:
                return authenticateRestApi(session, credentials);
            case SFTP:
                return authenticateSFTP(session, credentials);
            default:
                return AuthenticationResult.success(); // Default allow for unknown protocols
        }
    }

    private AuthenticationResult authenticateHL7(DataTransferSession session,
            Map<String, Object> credentials) {
        // HL7 typically uses MLLP with optional authentication
        // Check for authentication token if provided
        String authToken = (String) credentials.get("authToken");
        if (authToken != null) {
            // Validate token (simplified)
            if (!isValidToken(authToken)) {
                return AuthenticationResult.failure("Invalid HL7 authentication token");
            }
        }
        return AuthenticationResult.success();
    }

    private AuthenticationResult authenticateFHIR(DataTransferSession session,
            Map<String, Object> credentials) {
        // FHIR uses OAuth2/Bearer tokens
        String bearerToken = (String) credentials.get("bearerToken");
        if (bearerToken == null) {
            bearerToken = (String) credentials.get("authToken");
        }

        if (bearerToken != null && !isValidToken(bearerToken)) {
            return AuthenticationResult.failure("Invalid FHIR bearer token");
        }

        return AuthenticationResult.success();
    }

    private AuthenticationResult authenticateDICOM(DataTransferSession session,
            Map<String, Object> credentials) {
        // DICOM uses AE titles and potentially certificates
        String callingAET = (String) credentials.get("callingAET");
        String calledAET = (String) credentials.get("calledAET");

        if (callingAET == null || calledAET == null) {
            return AuthenticationResult.failure("DICOM AE titles required");
        }

        // Validate AE titles (simplified)
        if (!isValidAETitle(callingAET) || !isValidAETitle(calledAET)) {
            return AuthenticationResult.failure("Invalid DICOM AE titles");
        }

        return AuthenticationResult.success();
    }

    private AuthenticationResult authenticateRestApi(DataTransferSession session,
            Map<String, Object> credentials) {
        // REST API authentication (Basic, Bearer, API Key)
        String authType = (String) credentials.get("authType");
        if (authType == null) {
            return AuthenticationResult.success(); // Allow anonymous if not specified
        }

        switch (authType.toUpperCase()) {
            case "BASIC":
                String username = (String) credentials.get("username");
                String password = (String) credentials.get("password");
                if (username == null || password == null) {
                    return AuthenticationResult.failure("Basic auth requires username and password");
                }
                break;
            case "BEARER":
                String token = (String) credentials.get("token");
                if (token == null || !isValidToken(token)) {
                    return AuthenticationResult.failure("Invalid bearer token");
                }
                break;
            case "API_KEY":
                String apiKey = (String) credentials.get("apiKey");
                if (apiKey == null || !isValidApiKey(apiKey)) {
                    return AuthenticationResult.failure("Invalid API key");
                }
                break;
        }

        return AuthenticationResult.success();
    }

    private AuthenticationResult authenticateSFTP(DataTransferSession session,
            Map<String, Object> credentials) {
        // SFTP authentication (password or key-based)
        String username = (String) credentials.get("username");
        String password = (String) credentials.get("password");
        String privateKey = (String) credentials.get("privateKey");

        if (username == null) {
            return AuthenticationResult.failure("SFTP username required");
        }

        if (password == null && privateKey == null) {
            return AuthenticationResult.failure("SFTP requires password or private key");
        }

        return AuthenticationResult.success();
    }

    private boolean checkTransferTypePermission(DataTransferSession session, String operation) {
        // Check if the transfer type is allowed for the hospital/device combination
        // This would typically check against hospital policies
        return true; // Simplified - allow all for now
    }

    private boolean checkDataTypePermission(DataTransferSession session) {
        // Check if the data type is allowed
        // Hospitals may restrict certain data types
        return accessVerificationService.verifyDeviceAccess(
                session.getDevice().getDeviceId(),
                session.getHospital().getHospitalId(),
                session.getDataType().name());
    }

    private boolean checkProtocolPermission(DataTransferSession session) {
        // Check if the protocol is allowed for the hospital
        // Some hospitals may only allow certain protocols
        return true; // Simplified - allow all protocols
    }

    private boolean checkRateLimits(DataTransferSession session) {
        // Check rate limits and data transfer quotas
        // This would check against configured limits
        return true; // Simplified - no rate limiting
    }

    // Validation helper methods
    private boolean isValidToken(String token) {
        // Simplified token validation
        return token != null && token.length() > 10;
    }

    private boolean isValidAETitle(String aeTitle) {
        // DICOM AE titles are 1-16 characters
        return aeTitle != null && aeTitle.length() >= 1 && aeTitle.length() <= 16;
    }

    private boolean isValidApiKey(String apiKey) {
        // Simplified API key validation
        return apiKey != null && apiKey.length() > 20;
    }

    // Result classes
    public static class AuthenticationResult {
        private final boolean authenticated;
        private final String errorMessage;

        private AuthenticationResult(boolean authenticated, String errorMessage) {
            this.authenticated = authenticated;
            this.errorMessage = errorMessage;
        }

        public boolean isAuthenticated() {
            return authenticated;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public static AuthenticationResult success() {
            return new AuthenticationResult(true, null);
        }

        public static AuthenticationResult failure(String message) {
            return new AuthenticationResult(false, message);
        }
    }

    public static class AuthorizationResult {
        private final boolean allowed;
        private final String denialReason;

        private AuthorizationResult(boolean allowed, String denialReason) {
            this.allowed = allowed;
            this.denialReason = denialReason;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getDenialReason() {
            return denialReason;
        }

        public static AuthorizationResult allowed() {
            return new AuthorizationResult(true, null);
        }

        public static AuthorizationResult denied(String reason) {
            return new AuthorizationResult(false, reason);
        }
    }
}