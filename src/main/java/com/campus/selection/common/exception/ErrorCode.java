package com.campus.selection.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_ARGUMENT(40001, HttpStatus.BAD_REQUEST, "请求参数不合法"),
    UNAUTHORIZED(40101, HttpStatus.UNAUTHORIZED, "登录状态失效，请重新登录"),
    FORBIDDEN(40301, HttpStatus.FORBIDDEN, "没有权限执行该操作"),
    NOT_FOUND(40401, HttpStatus.NOT_FOUND, "请求的资源不存在"),
    CONFLICT(40901, HttpStatus.CONFLICT, "请求冲突，请稍后重试"),
    ALREADY_SELECTED(40902, HttpStatus.CONFLICT, "你已选择该课程"),
    SOLD_OUT(40903, HttpStatus.CONFLICT, "课程名额已抢完"),
    SELECTION_NOT_OPEN(40904, HttpStatus.CONFLICT, "当前不在选课时间内"),
    COURSE_NOT_AVAILABLE(40905, HttpStatus.CONFLICT, "课程未发布或已关闭"),
    CANCEL_NOT_ALLOWED(40906, HttpStatus.CONFLICT, "当前订单不可取消"),
    IDEMPOTENCY_CONFLICT(40907, HttpStatus.CONFLICT, "幂等键已被其他请求占用"),
    RATE_LIMITED(42901, HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后再试"),
    SERVICE_UNAVAILABLE(50301, HttpStatus.SERVICE_UNAVAILABLE, "服务暂时不可用，请稍后重试"),
    INTERNAL_ERROR(50001, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final int code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
