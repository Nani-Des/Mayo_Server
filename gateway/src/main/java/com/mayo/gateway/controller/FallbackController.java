package com.mayo.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Fallback controller for circuit breaker fallbacks
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncServiceFallback() {
        log.warn("SYNC SERVICE FALLBACK triggered - Circuit breaker is OPEN or half-open");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("code", "SERVICE_UNAVAILABLE");
        response.put("message", "Sync service is currently unavailable. Please try again later.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @GetMapping("/hospital")
    public ResponseEntity<Map<String, Object>> hospitalServiceFallback() {
        log.warn("HOSPITAL SERVICE FALLBACK triggered - Circuit breaker is OPEN or half-open for hospital-service");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("code", "SERVICE_UNAVAILABLE");
        response.put("message", "Hospital integration service is currently unavailable. Please try again later.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @GetMapping("/graphql")
    public ResponseEntity<Map<String, Object>> graphqlServiceFallback() {
        log.warn("GRAPHQL SERVICE FALLBACK triggered - Circuit breaker is OPEN or half-open");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("code", "SERVICE_UNAVAILABLE");
        response.put("message", "GraphQL service is currently unavailable. Please try again later.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}