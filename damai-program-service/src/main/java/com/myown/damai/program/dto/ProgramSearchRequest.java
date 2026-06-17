package com.myown.damai.program.dto;

import java.time.Instant;

/**
 * Carries normalized program search filters for Elasticsearch queries.
 */
public record ProgramSearchRequest(
        String keyword,
        Long areaId,
        Long programCategoryId,
        Integer timeType,
        Instant startTime,
        Instant endTime,
        Integer type,
        int pageNumber,
        int pageSize
) {
}
