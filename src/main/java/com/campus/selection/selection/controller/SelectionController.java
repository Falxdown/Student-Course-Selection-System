package com.campus.selection.selection.controller;

import com.campus.selection.common.api.ApiResponse;
import com.campus.selection.common.api.PageResponse;
import com.campus.selection.common.security.LoginUser;
import com.campus.selection.domain.OrderStatus;
import com.campus.selection.selection.dto.GrabRequest;
import com.campus.selection.selection.dto.GrabResponse;
import com.campus.selection.selection.dto.OrderResponse;
import com.campus.selection.selection.dto.TaskStatusResponse;
import com.campus.selection.selection.service.SelectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "03-抢选与订单")
@Validated
@RestController
@RequestMapping("/api/v1/selections")
@RequiredArgsConstructor
public class SelectionController {

    private final SelectionService selectionService;

    @Operation(summary = "抢选课程（异步受理）")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<GrabResponse> grab(
            @AuthenticationPrincipal LoginUser loginUser,
            @Parameter(description = "客户端幂等键，8-64 位字母、数字、下划线或短横线")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody GrabRequest request) {
        return ApiResponse.ok(selectionService.grab(loginUser, request.courseId(), idempotencyKey));
    }

    @Operation(summary = "查询异步抢选任务状态")
    @GetMapping("/tasks/{requestId}")
    public ApiResponse<TaskStatusResponse> task(@AuthenticationPrincipal LoginUser loginUser,
                                                @PathVariable String requestId) {
        return ApiResponse.ok(selectionService.getTask(loginUser, requestId));
    }

    @Operation(summary = "分页查询我的订单")
    @GetMapping("/orders")
    public ApiResponse<PageResponse<OrderResponse>> orders(
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(selectionService.listOrders(loginUser, status, page, size));
    }

    @Operation(summary = "查询订单详情")
    @GetMapping("/orders/{orderNo}")
    public ApiResponse<OrderResponse> order(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable String orderNo) {
        return ApiResponse.ok(selectionService.getOrder(loginUser, orderNo));
    }

    @Operation(summary = "取消选课并归还名额")
    @DeleteMapping("/orders/{orderNo}")
    public ApiResponse<OrderResponse> cancel(@AuthenticationPrincipal LoginUser loginUser,
                                             @PathVariable String orderNo) {
        return ApiResponse.ok(selectionService.cancel(loginUser, orderNo));
    }
}
