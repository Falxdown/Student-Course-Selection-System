package com.campus.selection.selection.dto;

import com.campus.selection.domain.OrderStatus;
import com.campus.selection.selection.entity.SelectionOrder;

import java.time.LocalDateTime;

public record OrderResponse(
        String orderNo,
        String requestId,
        Long courseId,
        String courseCode,
        String courseName,
        OrderStatus status,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt
) {
    public static OrderResponse from(SelectionOrder order) {
        return new OrderResponse(
                order.getOrderNo(),
                order.getRequestId(),
                order.getCourseId(),
                order.getCourseCode(),
                order.getCourseName(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getCancelledAt()
        );
    }
}
