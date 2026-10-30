package com.campus.selection.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.selection")
public record SelectionProperties(
        int rateLimitCapacity,
        double rateLimitRefillPerSecond,
        long idempotencyTtlSeconds,
        int hotCacheSeconds,
        int staleTaskSeconds,
        int reconcileBatchSize
) {
}
