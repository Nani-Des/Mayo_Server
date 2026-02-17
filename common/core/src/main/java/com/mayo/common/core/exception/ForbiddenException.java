package com.mayo.common.core.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when user lacks permission for an action
 */
public class ForbiddenException extends MayoException {

    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    public ForbiddenException() {
        super("Access denied", HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    public static ForbiddenException insufficientPermissions() {
        return new ForbiddenException("Insufficient permissions to perform this action");
    }

    public static ForbiddenException patientAccessDenied() {
        return new ForbiddenException("You don't have access to this patient's records");
    }
}
