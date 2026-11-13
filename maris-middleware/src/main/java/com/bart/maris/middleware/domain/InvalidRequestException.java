package com.bart.maris.middleware.domain;

/** The request breaks one of the old screen's input rules; the message is the old screen's text. Answered as 400. */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
