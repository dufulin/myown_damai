package com.myown.damai.order.lock;

import com.myown.damai.common.cache.DamaiCacheKey;
import com.myown.damai.common.exception.BusinessException;
import java.time.Duration;
import java.util.function.Supplier;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Uses Redisson distributed locks to serialize order creation by program id.
 */
@Component
@ConditionalOnProperty(value = "damai.order.lock.redisson-enabled", havingValue = "true", matchIfMissing = true)
public class RedissonOrderLockExecutor implements OrderLockExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedissonOrderLockExecutor.class);

    private final RedissonClient redissonClient;
    private final Duration waitTime;
    private final Duration leaseTime;

    /**
     * Creates the executor with Redisson and lock timing settings.
     */
    public RedissonOrderLockExecutor(
            RedissonClient redissonClient,
            @Value("${damai.order.lock.wait-seconds:3}") long waitSeconds,
            @Value("${damai.order.lock.lease-seconds:10}") long leaseSeconds
    ) {
        this.redissonClient = redissonClient;
        this.waitTime = Duration.ofSeconds(waitSeconds);
        this.leaseTime = Duration.ofSeconds(leaseSeconds);
    }

    /**
     * Runs the callback after acquiring the Redisson lock for one program id.
     */
    @Override
    public <T> T executeWithProgramLock(Long programId, Supplier<T> callback) {
        String lockKey = DamaiCacheKey.lock("order", "create-program", programId);
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            LOGGER.info("program order distributed lock result, programId={}, lockKey={}, locked={}", programId, lockKey, locked);
            if (!locked) {
                throw new BusinessException("ORDER_LOCK_BUSY", "program is busy, please try again later", HttpStatus.CONFLICT);
            }
            return callback.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("ORDER_LOCK_INTERRUPTED", "order lock interrupted", HttpStatus.CONFLICT);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                LOGGER.info("program order distributed lock released, programId={}, lockKey={}", programId, lockKey);
            }
        }
    }
}
