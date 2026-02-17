package com.mayo.common.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when user is not authorized to perform an action
 */
public class UnauthorizedException extends MayoException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    public UnauthorizedException() {
        super("Unauthorized access", HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    public static UnauthorizedException invalidToken() {
        return new UnauthorizedException("Invalid or expired token");
    }

    public static UnauthorizedException missingToken() {
        return new UnauthorizedException("Missing authentication token");
    }

    public static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("Invalid username or password");
    }
}
