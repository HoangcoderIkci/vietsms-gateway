package com.hoangcoder.vietsms.webhook.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Base exception for webhook-specific 4xx errors.
 * Carries an error code string (for the ApiError.error field) and HTTP status.
 */
public class WebhookException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public WebhookException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
