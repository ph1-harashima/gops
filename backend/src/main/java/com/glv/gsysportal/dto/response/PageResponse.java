package com.glv.gsysportal.dto.response;

import java.util.List;

/**
 * Generic Backend-Pagination envelope (Phase 8-G 5章/9章/15章's explicit
 * "Frontend全件取得後Filterは禁止 / Backend側でFilter/Paginationしてください"
 * instruction). No prior Screen in this codebase paginated on the Backend
 * (every existing List endpoint returns a plain {@code List<T>} - the
 * Demo Instance's row counts never required it); this is the first, and is
 * deliberately generic so a later Phase can reuse it rather than each new
 * List screen inventing its own envelope shape.
 *
 * <p>{@code page} is 0-indexed. {@code totalPages} is 0 when
 * {@code totalElements} is 0 (never negative, never divides by zero).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil(totalElements / (double) size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
