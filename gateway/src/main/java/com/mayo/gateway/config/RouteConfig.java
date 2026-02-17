package com.mayo.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mayo.gateway.filters.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gateway route configuration
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RouteConfig {

        private final JwtAuthFilter jwtAuthFilter;

        @Bean
        public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
                log.info("Configuring gateway routes");
                return builder.routes()
                                // Public routes (no auth required)
                                .route("public-auth", r -> r
                                                .path("/api/v1/auth/register",  
                                                      "/api/v1/auth/login", 
                                                      "/api/v1/auth/device-login",
                                                      "/api/v1/auth/devices/check-registration")
                                                .uri("http://localhost:8451"))

                                // Public device check endpoint (no auth required)
                                .route("public-device-check", r -> r
                                                .path("/api/v1/public/devices/check/**")
                                                .uri("http://localhost:8084"))

                                // Family Management and User Management Routes (auth required)
                                .route("auth-service-protected", r -> r
                                                .path("/api/v1/families/**", "/api/v1/users/**")
                                                .uri("http://localhost:8451"))

                                // Sync Service Routes (auth required)
                                .route("sync-service", r -> r
                                                .path("/api/v1/sync/**")
                                                .filters(f -> f.circuitBreaker(c -> c
                                                                .setName("sync-service-circuit-breaker")
                                                                .setFallbackUri("forward:/fallback/sync")))
                                                .uri("http://localhost:8448"))

                                // Hospital Integration Service Routes (auth required)
                                .route("hospital-service", r -> r
                                                .path("/api/v1/hospital/**", "/api/v1/hospitals/**")
                                                .uri("http://localhost:8447"))

                                // GraphQL Routes (auth required)
                                .route("graphql-service", r -> r
                                                .path("/graphql/**")
                                                .filters(f -> f.circuitBreaker(c -> c.setName(
                                                                "graphql-service-circuit-breaker")
                                                                .setFallbackUri("forward:/fallback/graphql")))
                                                .uri("http://localhost:8447"))

                                // Device Registry Service Routes (auth required)
                                .route("device-registry-service", r -> r
                                                .path("/api/v1/devices/**", "/api/v1/admin/devices/**")
                                                .uri("http://localhost:8084"))

                                // Patient Service Routes (auth required)
                                .route("patient-service", r -> r
                                                .path("/api/v1/patients/**")
                                                .uri("http://localhost:8445"))

                                // Patient Record Service Routes (auth required)
                                .route("patient-record-service", r -> r
                                                .path("/api/v1/patient-records/**")
                                                .uri("http://localhost:8446"))

                                // Appointment Service Routes (auth required)
                                .route("appointment-service", r -> r
                                                .path("/api/v1/appointments/**")
                                                .uri("http://localhost:8452"))

                                .build();
        }
}
