package com.myown.damai.order.dto;

/**
 * Exposes the number of orders canceled by a timeout scan.
 */
public record OrderTimeoutCancelResponse(int canceledCount) {
}
