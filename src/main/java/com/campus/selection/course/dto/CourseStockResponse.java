package com.campus.selection.course.dto;

import com.campus.selection.course.entity.Course;
import com.campus.selection.domain.CourseStatus;

import java.time.LocalDateTime;

public record CourseStockResponse(
        Long courseId,
        int capacity,
        int selectedCount,
        int remainingStock,
        boolean selectionOpen,
        LocalDateTime serverTime
) {
    public static CourseStockResponse from(Course course, int remainingStock) {
        LocalDateTime now = LocalDateTime.now();
        return new CourseStockResponse(
                course.getId(),
                course.getCapacity(),
                course.getSelectedCount(),
                remainingStock,
                course.getStatus() == CourseStatus.PUBLISHED
                        && !now.isBefore(course.getSelectStartTime())
                        && !now.isAfter(course.getSelectEndTime()),
                now
        );
    }
}
