package com.campus.selection.selection.controller;

import com.campus.selection.common.api.ApiResponse;
import com.campus.selection.selection.dto.SelectionStatisticsResponse;
import com.campus.selection.selection.service.SelectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "05-管理端课程")
@RestController
@RequestMapping("/api/v1/admin/selections")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSelectionController {

    private final SelectionService selectionService;

    @Operation(summary = "选课运营统计")
    @GetMapping("/statistics")
    public ApiResponse<SelectionStatisticsResponse> statistics() {
        return ApiResponse.ok(selectionService.statistics());
    }
}
