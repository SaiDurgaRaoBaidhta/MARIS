package com.bart.maris.middleware.legacy;

import com.bart.maris.middleware.legacy.control.XdControl;
import java.util.Map;

/**
 * Decoded output of one legacy call.
 *
 * @param records parameter name -> decoded record (Map) or primitive value, in the service's parameter order
 * @param control the XDCONTROL parameter, or null for services without one
 */
public record ProgramResult(String function, String service, Map<String, Object> records, XdControl control, long durationMs) {

    @SuppressWarnings("unchecked")
    public Map<String, Object> record(String paramName) {
        Object r = records.get(paramName);
        if (!(r instanceof Map)) throw new IllegalArgumentException(function + " has no record parameter " + paramName);
        return (Map<String, Object>) r;
    }
}
