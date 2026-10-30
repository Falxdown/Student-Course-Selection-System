package com.campus.selection.course.controller;

import com.campus.selection.common.api.ApiResponse;
import com.campus.selection.common.api.PageResponse;
import com.campus.selection.course.dto.CourseResponse;
import com.campus.selection.course.dto.CourseStockResponse;
import com.campus.selection.course.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "02-课程")
@Validated
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @Operation(summary = "分页查询已发布课程")
    @GetMapping
    public ApiResponse<PageResponse<CourseResponse>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "hot") String sort) {
        return ApiResponse.ok(courseService.listPublished(page, size, keyword, category, sort));
    }

    @Operation(summary = "热门课程 Top N")
    @GetMapping("/hot")
    public ApiResponse<List<CourseResponse>> hot(
            @RequestParam(defaultValue = "6") @Min(1) @Max(20) int limit) {
        return ApiResponse.ok(courseService.hot(limit));
    }

    @Operation(summary = "课程详情")
    @GetMapping("/{courseId}")
    public ApiResponse<CourseResponse> detail(@PathVariable Long courseId) {
        return ApiResponse.ok(courseService.getPublished(courseId));
    }

    @Operation(summary = "查询课程实时余量（Redis）")
    @GetMapping("/{courseId}/stock")
    public ApiResponse<CourseStockResponse> stock(@PathVariable Long courseId) {
        return ApiResponse.ok(courseService.stock(courseId));
    }
}
