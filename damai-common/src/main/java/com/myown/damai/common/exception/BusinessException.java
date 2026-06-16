package com.myown.damai.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Represents an expected business failure that should be returned as an API error.
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    /**
     * Creates a business exception with a stable code, message, and HTTP status.
     */
    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /**
     * Returns the stable API error code.
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the HTTP status that should be sent to the client.
     */
    public HttpStatus getStatus() {
        return status;
    }
}
