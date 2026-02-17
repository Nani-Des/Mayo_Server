package com.mayo.common.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for all Mayo EMR custom exceptions
 */
@Getter
public class MayoException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public MayoException(String message) {
        super(message);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
        this.errorCode = "MAYO_ERROR";
    }

    public MayoException(String message, HttpStatus status) {
        super(message);
        this.status = status;
        this.errorCode = "MAYO_ERROR";
    }

    public MayoException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public MayoException(String message, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
        this.errorCode = "MAYO_ERROR";
    }

    public MayoException(String message, Throwable cause, HttpStatus status) {
        super(message, cause);
        this.status = status;
        this.errorCode = "MAYO_ERROR";
    }
}
