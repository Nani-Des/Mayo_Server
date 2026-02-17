package com.mayo.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter that validates all incoming requests contain the gateway secret header.
 * This ensures requests can only come through the API gateway.
 */
@Component
public class GatewayValidationFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(GatewayValidationFilter.class);
    private static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";

    @Value("${gateway.secret}")
    private String expectedSecret;

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

        // Valid gateway secret - proceed with request
        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("GatewayValidationFilter initialized - direct service access is now blocked");
    }

    @Override
    public void destroy() {
        // Cleanup if needed
    }
}
