package com.bart.maris.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/** One REST service of MARIS_RUI_Services_Web: service name, JSON-RPC method and its ordered parameters. */
public final class ServiceDef {
    public String service;
    public String function;
    public String program;
    public String pkg;
    public List<Param> params = new ArrayList<>();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Param {
        public String name;
        public String kind;        // RECORD, PRIMITIVE or PROGRAM_RETURN
        public String record;      // qualified record name, for RECORD
        public String primitiveType; // STRING, INT, ... for PRIMITIVE
        public Param() {}
        Param(String name, String kind, String record) {
            this.name = name;
            this.kind = kind;
            this.record = record;
        }
    }
}
