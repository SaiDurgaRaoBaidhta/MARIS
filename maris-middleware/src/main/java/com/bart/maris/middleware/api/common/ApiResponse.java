package com.bart.maris.middleware.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Envelope for successful responses: the payload plus optional metadata (legacy control info, timing). */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiResponse<T>(T data, Map<String, Object> meta) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, Map.of());
    }

    public static <T> ApiResponse<T> of(T data, Map<String, Object> meta) {
        return new ApiResponse<>(data, meta);
    }
}
