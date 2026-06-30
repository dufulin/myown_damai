package com.myown.damai.admin.dto;

import java.time.Instant;

/**
 * Exposes program status and aggregated ticket inventory for operations.
 */
public record AdminProgramResponse(
        Long id,
        String title,
        Long areaId,
        Long programCategoryId,
        Integer programStatus,
        Long totalStock,
        Long remainingStock,
        Instant issueTime,
        Instant createdAt
) {
}
