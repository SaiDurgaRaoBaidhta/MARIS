package com.bart.maris.middleware.legacy.client;

/** The EGL backend could not be reached in time (connection refused, timeout, circuit breaker open). */
public class LegacyUnavailableException extends RuntimeException {
    public LegacyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
