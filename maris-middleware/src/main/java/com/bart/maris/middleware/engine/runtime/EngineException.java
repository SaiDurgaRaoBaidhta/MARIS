package com.bart.maris.middleware.engine.runtime;

/** The old screen logic could not be run (unknown name, bad index ...). The message names the place. */
public class EngineException extends RuntimeException {
    public EngineException(String message) {
        super(message);
    }

    public EngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
