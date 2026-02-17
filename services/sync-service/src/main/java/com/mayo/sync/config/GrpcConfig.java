package com.mayo.sync.config;

import net.devh.boot.grpc.server.interceptor.GlobalServerInterceptorConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC configuration for sync service
 */
@Configuration
public class GrpcConfig {

    @Bean
    public GlobalServerInterceptorConfigurer globalInterceptorConfigurerAdapter() {
        return registry -> {
            // Add global interceptors here if needed
            // registry.addServerInterceptors(new AuthInterceptor());
        };
    }
}