package com.bart.maris.middleware.legacy;

public class UnknownProgramException extends RuntimeException {
    public UnknownProgramException(String name) {
        super("No MARIS service runs program " + name);
    }
}
