package com.campus.selection.selection.service;

import com.campus.selection.common.cache.CacheKeys;
import com.campus.selection.common.config.SelectionProperties;
import com.campus.selection.common.exception.BusinessException;
import com.campus.selection.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class RateLimitService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> tokenBucketScript;
    private final SelectionProperties properties;

    public RateLimitService(StringRedisTemplate redis,
                            @Qualifier("tokenBucketScript") DefaultRedisScript<Long> tokenBucketScript,
                            SelectionProperties properties) {
        this.redis = redis;
        this.tokenBucketScript = tokenBucketScript;
        this.properties = properties;
    }

    public void check(String scope, String identity) {
        String key = CacheKeys.rateLimit(scope, identity);
        long now = System.currentTimeMillis();
        String nowSeconds = String.format(Locale.ROOT, "%.3f", now / 1000.0);
        try {
            Long allowed = redis.execute(
                    tokenBucketScript,
                    List.of(key),
                    String.valueOf(properties.rateLimitCapacity()),
                    String.valueOf(properties.rateLimitRefillPerSecond()),
                    nowSeconds,
                    "120"
            );
            if (allowed == null || allowed != 1L) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
        } catch (DataAccessException ex) {
            log.error("Token bucket check failed, scope={}, identity={}", scope, identity, ex);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "限流服务暂时不可用，请稍后重试", ex);
        }
    }
}
