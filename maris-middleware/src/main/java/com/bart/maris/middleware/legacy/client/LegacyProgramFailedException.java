package com.bart.maris.middleware.legacy.client;

/** The service ran but the EGL call failed: PROGRAM_RETURN_REC.errorCode is not 00000000. */
public class LegacyProgramFailedException extends RuntimeException {
    private final String program;
    private final String errorCode;

    public LegacyProgramFailedException(String program, String errorCode) {
        super("Legacy program " + program + " ended with error code " + errorCode);
        this.program = program;
        this.errorCode = errorCode;
    }

    public String program() {
        return program;
    }

    public String errorCode() {
        return errorCode;
    }
}
