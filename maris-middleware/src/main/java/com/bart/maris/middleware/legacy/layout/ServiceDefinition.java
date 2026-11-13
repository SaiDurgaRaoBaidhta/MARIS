package com.bart.maris.middleware.legacy.layout;

import java.util.List;

/**
 * One REST service of MARIS_RUI_Services_Web and the JSON-RPC method it exposes.
 *
 * @param service  e.g. {@code MRS02AService} (URL path segment)
 * @param function JSON-RPC method name, e.g. {@code MRS02A}
 * @param program  EGL program the service calls (null for the two print services)
 */
public record ServiceDefinition(String service, String function, String program, String pkg, List<Param> params) {
    public ServiceDefinition {
        params = params == null ? List.of() : List.copyOf(params);
    }

    public enum ParamKind { RECORD, PRIMITIVE, PROGRAM_RETURN }

    public record Param(String name, ParamKind kind, String record, String primitiveType) {}
}
