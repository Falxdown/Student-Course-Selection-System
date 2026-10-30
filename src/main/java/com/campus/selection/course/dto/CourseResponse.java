package com.campus.selection.course.dto;

import com.campus.selection.course.entity.Course;
import com.campus.selection.domain.CourseStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CourseResponse(
        Long id,
        String courseCode,
        String name,
        String teacher,
        String category,
        BigDecimal credits,
        Integer capacity,
        Integer selectedCount,
        Integer remainingStock,
        LocalDateTime selectStartTime,
        LocalDateTime selectEndTime,
        CourseStatus status,
        String description,
        boolean selectionOpen
) {
    public static CourseResponse from(Course course, int remainingStock) {
        return new CourseResponse(
                course.getId(),
                course.getCourseCode(),
                course.getName(),
                course.getTeacher(),
                course.getCategory(),
                course.getCredits(),
                course.getCapacity(),
                course.getSelectedCount(),
                remainingStock,
                course.getSelectStartTime(),
                course.getSelectEndTime(),
                course.getStatus(),
                course.getDescription(),
                course.getStatus() == CourseStatus.PUBLISHED
                        && !LocalDateTime.now().isBefore(course.getSelectStartTime())
                        && !LocalDateTime.now().isAfter(course.getSelectEndTime())
        );
    }
}
