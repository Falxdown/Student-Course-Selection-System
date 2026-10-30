package com.campus.selection.course.controller;

import com.campus.selection.common.api.ApiResponse;
import com.campus.selection.course.dto.CourseRequest;
import com.campus.selection.course.dto.CourseResponse;
import com.campus.selection.course.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "05-管理端课程")
@RestController
@RequestMapping("/api/v1/admin/courses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCourseController {

    private final CourseService courseService;

    @Operation(summary = "新增课程")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CourseResponse> create(@Valid @RequestBody CourseRequest request) {
        return ApiResponse.ok(courseService.create(request));
    }

    @Operation(summary = "编辑课程（含发布/停用）")
    @PutMapping("/{courseId}")
    public ApiResponse<CourseResponse> update(@PathVariable Long courseId,
                                              @Valid @RequestBody CourseRequest request) {
        return ApiResponse.ok(courseService.update(courseId, request));
    }
}
