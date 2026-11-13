package com.bart.maris.middleware.legacy.client;

/** The EGL backend answered, but not with a valid JSON-RPC result (HTTP error page, malformed body). */
public class LegacyProtocolException extends RuntimeException {
    private final int httpStatus;

    public LegacyProtocolException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
