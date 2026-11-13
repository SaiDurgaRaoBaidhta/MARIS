package com.bart.maris.middleware.domain.screens;

/** The button exists on the screen, but its logic has not been translated into the middleware yet. */
public class ActionNotMigratedException extends RuntimeException {
    public ActionNotMigratedException(String screen, String control, String caption) {
        super("\"" + (caption == null || caption.isBlank() ? control : caption.strip()) + "\" on " + screen
                + " is not connected to the MARIS system yet.");
    }
}
