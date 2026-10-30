package com.campus.selection.common.api;

import java.util.List;

public record PageResponse<T>(List<T> records, long total, long page, long size, long pages) {

    public static <T> PageResponse<T> of(List<T> records, long total, long page, long size) {
        long pages = size <= 0 ? 0 : (total + size - 1) / size;
        return new PageResponse<>(records, total, page, size, pages);
    }
}
