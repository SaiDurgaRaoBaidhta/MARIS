package com.bart.maris.middleware.legacy.codec;

/** A value does not fit the record layout (too long, too many digits, wrong type) or wire data is malformed. */
public class CodecException extends RuntimeException {
    private final String field;

    public CodecException(String field, String message) {
        super(field == null ? message : field + ": " + message);
        this.field = field;
    }

    /** Path of the offending field, e.g. {@code MRS02A_CRIT.userGrp}, or null. */
    public String field() {
        return field;
    }
}
