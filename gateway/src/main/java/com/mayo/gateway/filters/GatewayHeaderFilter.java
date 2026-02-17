package com.mayo.gateway.filters;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway filter that injects a secret header into all outbound requests to backend services.
 * This header is used by backend services to verify requests came through the gateway.
 */
@Component
public class GatewayHeaderFilter implements GlobalFilter, Ordered {

    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";

    @Value("${gateway.secret}")
    private String gatewaySecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Add the gateway secret header to the request
        ServerHttpRequest modifiedRequest = exchange.getRequest()
                .mutate()
                .header(GATEWAY_SECRET_HEADER, gatewaySecret)
                .build();

        ServerWebExchange modifiedExchange = exchange.mutate()
                .request(modifiedRequest)
                .build();

        return chain.filter(modifiedExchange);
    }

    @Override
    public int getOrder() {
        // High priority - run early in the filter chain
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
