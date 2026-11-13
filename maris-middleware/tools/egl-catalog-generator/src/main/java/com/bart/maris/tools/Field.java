package com.bart.maris.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/** One field of a fixed-layout EGL record. Offsets of children are relative to the start of one parent element. */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public final class Field {
    public String name;
    public String json;
    public String type;
    public int length;
    public int decimals;
    public int bytes;          // bytes of ONE element
    public int occurs = 1;
    public int offset;         // relative to the parent element (or record start for top-level fields)
    public boolean filler;
    public List<Field> children = new ArrayList<>();

    @com.fasterxml.jackson.annotation.JsonIgnore
    int level;
    @com.fasterxml.jackson.annotation.JsonIgnore
    int cursor;                // next free byte inside this element, while children are parsed

    /** Bytes for a primitive EGL type, as the gt2 runtime lays it out. */
    static int bytesFor(String type, int length) {
        return switch (type) {
            case "CHAR", "MBCHAR", "NUM", "NUMC" -> length;
            case "DECIMAL", "PACF" -> (length + 2) / 2;          // ceil((length+1)/2)
            case "SMALLINT" -> 2;
            case "INT" -> 4;
            case "BIGINT" -> 8;
            case "BIN" -> switch (length) { case 4 -> 2; case 9 -> 4; case 18 -> 8; default -> -1; };
            case "HEX" -> length / 2;
            case "DBCHAR", "UNICODE" -> length * 2;
            default -> -1;
        };
    }

    /** Implicit length of types written without a length. */
    static int defaultLength(String type) {
        return switch (type) {
            case "SMALLINT" -> 4;
            case "INT" -> 9;
            case "BIGINT" -> 18;
            default -> 0;
        };
    }
}
