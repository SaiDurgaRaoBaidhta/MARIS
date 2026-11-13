package com.bart.maris.middleware.domain;

import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.control.XdControl;

/** A legacy program reported an error in XDCONTROL (URC other than 0 or 4). */
public class LegacyBusinessException extends RuntimeException {
    private final String program;
    private final XdControl control;
    private final MessageResolver.Message message;

    public LegacyBusinessException(String program, XdControl control, MessageResolver.Message message) {
        super(message != null && message.text() != null ? message.text()
                : "Program " + program + " returned code " + control.returnCode());
        this.program = program;
        this.control = control;
        this.message = message;
    }

    public String program() {
        return program;
    }

    public XdControl control() {
        return control;
    }

    public MessageResolver.Message message() {
        return message;
    }
}
