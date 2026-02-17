package com.mayo.gateway.filters;

import com.mayo.common.security.jwt.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * JWT Authentication Filter for Gateway
 */
@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${gateway.secret}")
    private String gatewaySecret;

    @Override
    public int getOrder() {
        return -100; // Run early in the filter chain
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        log.info("JwtAuthFilter executing for path: {}", path);

        // Skip auth for public endpoints
        if (path.contains("/public/") || 
            path.contains("/v1/public/") ||
            path.contains("/auth/login") || 
            path.contains("/v1/auth/login") || 
            path.contains("/auth/register") ||
            path.contains("/v1/auth/register") ||
            path.contains("/auth/devices/check-registration") ||
            path.contains("/v1/auth/devices/check-registration")) {
            log.info("Bypassing auth for public path: {}", path);
            return chain.filter(exchange);
        }
        
        log.debug("Intercepted request to: {}", path);
        String authHeader = request.getHeaders().getFirst("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        
        String token = authHeader.substring(7);
        log.debug("Validating token for path: {}", path);
        
        try {
            if (!jwtTokenProvider.validateToken(token)) {
                log.warn("Invalid JWT token for path: {}", path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
            
            log.debug("Token validated successfully for path: {}", path);
            
            // Add user info to headers for downstream services
            String userId = jwtTokenProvider.getUserIdFromToken(token).toString();
            String userType = jwtTokenProvider.getUserTypeFromToken(token).name();
            String permissions = String.join(",", jwtTokenProvider.getPermissionsFromToken(token)
                    .stream().map(Enum::name).toList());

            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Type", userType)
                    .header("X-User-Permissions", permissions)
                    .header("X-Gateway-Secret", gatewaySecret)
                    .build();
            
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
            
        } catch (Exception e) {
            log.error("Error validating token", e);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }
}
