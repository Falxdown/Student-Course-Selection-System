package com.campus.selection.selection.dto;

public record SelectionStatisticsResponse(
        long publishedCourses,
        long totalOrders,
        long confirmedOrders,
        long cancelledOrders,
        long todayOrders,
        long totalTasks,
        long pendingTasks,
        long successTasks,
        long failedTasks,
        double taskSuccessRate
) {
}
