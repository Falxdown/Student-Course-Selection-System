package com.campus.selection.selection.dto;

public record GrabResponse(
        String requestId,
        String status,
        String orderNo,
        String message
) {
}
