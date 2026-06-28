package com.myown.damai.gateway.filter;

/**
 * Describes one fixed-window gateway rate limit dimension.
 */
public record GatewayRateLimitRule(
        String scope,
        String subject,
        int maxRequests,
        long windowSeconds
) {
}
