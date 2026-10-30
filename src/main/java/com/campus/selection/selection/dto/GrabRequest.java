package com.campus.selection.selection.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GrabRequest(
        @NotNull(message = "课程 ID 不能为空")
        @Positive(message = "课程 ID 必须为正数")
        Long courseId
) {
}
