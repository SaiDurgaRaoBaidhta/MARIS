package com.bart.maris.middleware.domain.auth;

/** Outcome of MRS02A. {@code messageText} is the old screen's message for a failed sign-on. */
public record SignonResult(Status status, String groupId, String groupDescription, String menu, String userType,
                           String yard, String incidentPrefix, String messageCode, String messageText) {
    public enum Status { VALID, WRONG_PASSWORD, UNKNOWN_GROUP }
}
