package com.myown.damai.admin.dto;

import java.util.List;

/**
 * Exposes one bounded management page with an exact total count.
 */
public record AdminPageResponse<T>(
        List<T> items,
        long total,
        int pageNumber,
        int pageSize
) {

    /**
     * Creates an immutable management page.
     */
    public static <T> AdminPageResponse<T> of(List<T> items, long total, int pageNumber, int pageSize) {
        return new AdminPageResponse<>(List.copyOf(items), total, pageNumber, pageSize);
    }
}
