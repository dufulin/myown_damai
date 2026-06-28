package com.myown.damai.order.dto;

import java.time.Instant;
import java.util.List;

/**
 * Represents a cursor-based order page response for large user order lists.
 */
public record OrderCursorPageResponse(
        List<OrderResponse> orders,
        Instant nextCursorCreateTime,
        Long nextCursorId,
        boolean hasMore
) {

    /**
     * Builds a cursor response with an empty next cursor when there are no more rows.
     */
    public static OrderCursorPageResponse of(List<OrderResponse> orders, Instant nextCursorCreateTime, Long nextCursorId, boolean hasMore) {
        return new OrderCursorPageResponse(
                orders,
                hasMore ? nextCursorCreateTime : null,
                hasMore ? nextCursorId : null,
                hasMore
        );
    }
}
