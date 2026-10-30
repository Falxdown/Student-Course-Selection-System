package com.campus.selection.course.dto;

import com.campus.selection.domain.CourseStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CourseRequest(
        @NotBlank(message = "课程编号不能为空")
        @Size(max = 32, message = "课程编号不能超过 32 个字符")
        String courseCode,

        @NotBlank(message = "课程名称不能为空")
        @Size(max = 128, message = "课程名称不能超过 128 个字符")
        String name,

        @NotBlank(message = "授课教师不能为空")
        @Size(max = 64, message = "授课教师不能超过 64 个字符")
        String teacher,

        @NotBlank(message = "课程类别不能为空")
        @Size(max = 32, message = "课程类别不能超过 32 个字符")
        String category,

        @NotNull(message = "学分不能为空")
        @DecimalMin(value = "0.5", message = "学分不能小于 0.5")
        @DecimalMax(value = "20.0", message = "学分不能大于 20")
        BigDecimal credits,

        @NotNull(message = "容量不能为空")
        @Min(value = 1, message = "容量至少为 1")
        @Max(value = 10000, message = "容量不能超过 10000")
        Integer capacity,

        @NotNull(message = "选课开始时间不能为空")
        LocalDateTime selectStartTime,

        @NotNull(message = "选课结束时间不能为空")
        LocalDateTime selectEndTime,

        @NotNull(message = "课程状态不能为空")
        CourseStatus status,

        @Size(max = 500, message = "课程简介不能超过 500 个字符")
        String description
) {
}
