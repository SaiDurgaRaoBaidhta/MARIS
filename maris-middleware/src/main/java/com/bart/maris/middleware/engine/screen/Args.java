package com.bart.maris.middleware.engine.screen;

import java.util.ArrayList;
import java.util.List;

/** action arguments with their parameter names (createConstantParameter / createAttributeParameter names) */
public final class Args extends ArrayList<Object> {
    final List<String> names = new ArrayList<>();

    void add(String name, Object value) {
        names.add(name);
        add(value);
    }

    Object named(String name, int fallback) {
        int i = names.indexOf(name);
        if (i >= 0) return get(i);
        return fallback < size() ? get(fallback) : null;
    }
}
