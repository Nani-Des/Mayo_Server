package com.mayo.gateway.filters;

import com.mayo.common.security.audit.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Web Application Firewall filter for common security threats
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WAFFilter implements GlobalFilter, Ordered {

    private final SecurityAuditService securityAuditService;

    // SQL Injection patterns
    private static final List<Pattern> SQL_INJECTION_PATTERNS = List.of(
        Pattern.compile("(?i)(union.*select|select.*from|insert.*into|update.*set|delete.*from)"),
        Pattern.compile("(?i)(drop\\s+table|alter\\s+table|create\\s+table)"),
        Pattern.compile("(?i)(--|#|;|/\\*|\\*/|\\*/)")
    );

    // XSS patterns
    private static final List<Pattern> XSS_PATTERNS = List.of(
        Pattern.compile("(?i)(<script|<iframe|<object|<embed|<form|<input|<meta|<link|<style)"),
        Pattern.compile("(?i)(javascript:|vbscript:|onload=|onerror=|onclick=|onmouseover=)"),
        Pattern.compile("(?i)(<.*>.*</.*>)")
    );

    // Path traversal patterns
    private static final List<Pattern> PATH_TRAVERSAL_PATTERNS = List.of(
        Pattern.compile("\\.\\./|\\.\\.\\\\"),
        Pattern.compile("%2e%2e%2f|%2e%2e/"),
        Pattern.compile("\\.\\.%2f|\\.\\.%5c")
    );

    // Command injection patterns
    private static final List<Pattern> COMMAND_INJECTION_PATTERNS = List.of(
        Pattern.compile("(?i)(;|\\||&|\\$\\(|`|\\$\\{)"),
        Pattern.compile("(?i)(rm\\s+|del\\s+|format\\s+|shutdown\\s+)")
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String query = request.getURI().getQuery();
        String method = request.getMethod().name();

        // Check path for malicious patterns
        if (containsMaliciousPattern(path, SQL_INJECTION_PATTERNS) ||
            containsMaliciousPattern(path, XSS_PATTERNS) ||
            containsMaliciousPattern(path, PATH_TRAVERSAL_PATTERNS) ||
            containsMaliciousPattern(path, COMMAND_INJECTION_PATTERNS)) {
            log.warn("WAF blocked request with malicious path: {} {}", method, path);
            securityAuditService.logSecurityViolation("WAF_BLOCKED_REQUEST",
                "Malicious path pattern detected: " + path,
                getClientIp(request), getUserAgent(request), "WARN");
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        // Check query parameters (skip command injection check for query strings - they naturally contain &)
        if (query != null && (
            containsMaliciousPattern(query, SQL_INJECTION_PATTERNS) ||
            containsMaliciousPattern(query, XSS_PATTERNS) ||
            containsMaliciousPattern(query, PATH_TRAVERSAL_PATTERNS))) {
            log.warn("WAF blocked request with malicious query: {} {}?{}", method, path, query);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        // Headers that should be excluded from command injection checks (standard browser headers)
        List<String> excludedHeaders = List.of(
            "user-agent", "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform",
            "accept", "accept-language", "accept-encoding", "referer", "cookie"
        );

        // Check headers for malicious content (skip standard browser headers for command injection check)
        for (String headerName : request.getHeaders().keySet()) {
            List<String> headerValues = request.getHeaders().get(headerName);
            if (headerValues != null) {
                for (String headerValue : headerValues) {
                    // Check for XSS in all headers
                    if (containsMaliciousPattern(headerValue, XSS_PATTERNS)) {
                        log.warn("WAF blocked request with malicious header: {}: {}", headerName, headerValue);
                        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                        return exchange.getResponse().setComplete();
                    }
                    // Skip standard browser headers for command injection check
                    if (!excludedHeaders.contains(headerName.toLowerCase()) && 
                        containsMaliciousPattern(headerValue, COMMAND_INJECTION_PATTERNS)) {
                        log.warn("WAF blocked request with malicious header: {}: {}", headerName, headerValue);
                        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                        return exchange.getResponse().setComplete();
                    }
                }
            }
        }

        // Check for suspicious request patterns
        if (isSuspiciousRequest(request)) {
            log.warn("WAF blocked suspicious request: {} {}", method, path);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    private boolean containsMaliciousPattern(String input, List<Pattern> patterns) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        return patterns.stream().anyMatch(pattern -> pattern.matcher(input).find());
    }

    private boolean isSuspiciousRequest(ServerHttpRequest request) {
        // Check for unusually long URLs
        String uri = request.getURI().toString();
        if (uri.length() > 2048) {
            return true;
        }

        // Check for too many query parameters
        String query = request.getURI().getQuery();
        if (query != null && query.split("&").length > 20) {
            return true;
        }

        // Check for known malicious user agents only (not blocking legitimate browsers)
        List<String> userAgents = request.getHeaders().get("User-Agent");
        if (userAgents != null) {
            for (String ua : userAgents) {
                String lowerUa = ua != null ? ua.toLowerCase() : "";
                // Only block known scanning/attack tools
                if (lowerUa.contains("sqlmap") || 
                    lowerUa.contains("nikto") ||
                    lowerUa.contains("nmap") ||
                    lowerUa.contains("masscan") ||
                    lowerUa.contains("burpsuite")) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1; // Run before other filters
    }

    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddress() != null ?
               request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    private String getUserAgent(ServerHttpRequest request) {
        return request.getHeaders().getFirst("User-Agent");
    }
}