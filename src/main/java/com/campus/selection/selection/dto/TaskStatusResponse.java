package com.campus.selection.selection.dto;

import com.campus.selection.domain.TaskStatus;
import com.campus.selection.selection.entity.SelectionTask;

import java.time.LocalDateTime;

public record TaskStatusResponse(
        String requestId,
        Long courseId,
        TaskStatus status,
        String orderNo,
        String failReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TaskStatusResponse from(SelectionTask task) {
        return new TaskStatusResponse(
                task.getRequestId(),
                task.getCourseId(),
                task.getStatus(),
                task.getOrderNo(),
                task.getFailReason(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
