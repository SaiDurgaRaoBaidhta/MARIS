package com.bart.maris.tools;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

/** A parsed EGL basicRecord with a fixed byte layout. */
public final class RecordDef {
    public String name;
    public String pkg;
    public int length;
    public List<Field> fields = new ArrayList<>();
    @JsonIgnore public String file;
    @JsonIgnore public List<String> problems = new ArrayList<>();
    @JsonIgnore public boolean fixed = true;

    @JsonIgnore
    public String qualifiedName() {
        return pkg + "." + name;
    }
}
