package com.hoangcoder.vietsms.common.exceptions;

public class TooEarlyException extends RuntimeException {
    private final long retryAfterSeconds;

    public TooEarlyException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
