package com.mayo.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * REST client for auth service to retrieve user profile data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestAuthClient {

    @Value("${auth.service.url:http://auth-service:8081}")
    private String authServiceUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Get user profile by ID
     */
    public UserProfile getUserById(UUID userId) {
        try {
            log.debug("Fetching user profile for user: {}", userId);

            String url = authServiceUrl + "/api/auth/users/" + userId;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<AuthResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, AuthResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody().getData();
            } else {
                log.warn("User {} not found in auth service (status: {})", userId, response.getStatusCode());
                return null;
            }

        } catch (Exception e) {
            log.error("Failed to fetch user profile for user: {}", userId, e);
            return null;
        }
    }

    /**
     * User profile DTO
     */
    public static class UserProfile {
        private UUID id;
        private String email;
        private String phoneNumber;
        private String fullName;

        // Getters and setters
        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
    }

    /**
     * Auth service response wrapper
     */
    private static class AuthResponse {
        private UserProfile data;

        public UserProfile getData() { return data; }
        public void setData(UserProfile data) { this.data = data; }
    }
}