package com.bart.maris.middleware.legacy.layout;

import java.util.List;

/** Fixed byte layout of one EGL basicRecord. */
public record RecordLayout(String name, String pkg, int length, List<FieldLayout> fields) {
    public RecordLayout {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public String qualifiedName() {
        return pkg + "." + name;
    }
}
