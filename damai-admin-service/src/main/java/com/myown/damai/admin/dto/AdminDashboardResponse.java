package com.myown.damai.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Exposes the management overview counters and latest orders.
 */
public record AdminDashboardResponse(
        long totalUsers,
        long activePrograms,
        long pendingPaymentOrders,
        long paidOrders,
        long timeoutOrders,
        BigDecimal paidAmount,
        List<AdminOrderResponse> recentOrders
) {

    /**
     * Defensively copies the recent-order collection.
     */
    public AdminDashboardResponse {
        paidAmount = paidAmount == null ? BigDecimal.ZERO : paidAmount;
        recentOrders = List.copyOf(recentOrders);
    }
}
