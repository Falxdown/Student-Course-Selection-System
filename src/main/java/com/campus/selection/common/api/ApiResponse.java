package com.campus.selection.common.api;

import org.slf4j.MDC;

public record ApiResponse<T>(int code, String message, T data, String traceId, long timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "成功", data, MDC.get("traceId"), System.currentTimeMillis());
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null, MDC.get("traceId"), System.currentTimeMillis());
    }
}
