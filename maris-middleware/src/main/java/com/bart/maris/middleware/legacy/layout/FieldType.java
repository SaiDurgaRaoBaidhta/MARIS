package com.bart.maris.middleware.legacy.layout;

/** EGL primitive types used by MARIS records, plus GROUP for an untyped substructure. */
public enum FieldType {
    CHAR, MBCHAR, HEX,
    NUM, NUMC,              // zoned decimal, ASCII digits as the gt2 browser layer writes them
    DECIMAL, PACF,          // packed decimal, sign nibble C/D (DECIMAL) or F/D (PACF)
    SMALLINT, INT, BIGINT, BIN,
    GROUP;

    public boolean isText() {
        return this == CHAR || this == MBCHAR;
    }

    public boolean isZoned() {
        return this == NUM || this == NUMC;
    }

    public boolean isPacked() {
        return this == DECIMAL || this == PACF;
    }

    public boolean isBinary() {
        return this == SMALLINT || this == INT || this == BIGINT || this == BIN;
    }
}
