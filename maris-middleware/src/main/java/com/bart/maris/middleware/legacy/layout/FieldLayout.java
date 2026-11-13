package com.bart.maris.middleware.legacy.layout;

import java.util.List;

/**
 * One field of a fixed-layout record, as generated from the EGL source.
 *
 * @param name     EGL name, e.g. {@code USER_GRP}
 * @param json     JSON property name, e.g. {@code userGrp}
 * @param bytes    bytes of ONE element
 * @param occurs   array size (1 when not an array)
 * @param offset   offset relative to the start of the parent element (record start for top-level fields)
 * @param children substructure fields that overlay this field's bytes
 */
public record FieldLayout(String name, String json, FieldType type, int length, int decimals, int bytes,
                          int occurs, int offset, boolean filler, List<FieldLayout> children) {
    public FieldLayout {
        occurs = Math.max(occurs, 1);
        children = children == null ? List.of() : List.copyOf(children);
    }

    public boolean isGroup() {
        return !children.isEmpty();
    }

    public boolean isArray() {
        return occurs > 1;
    }
}
