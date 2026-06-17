package com.myown.damai.order.lock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Provides an in-memory fallback lock for tests or local runs without Redis.
 */
@Component
@ConditionalOnProperty(value = "damai.order.lock.redisson-enabled", havingValue = "false")
public class LocalOrderLockExecutor implements OrderLockExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalOrderLockExecutor.class);

    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Runs the callback after acquiring the local fallback lock for one program id.
     */
    @Override
    public <T> T executeWithProgramLock(Long programId, Supplier<T> callback) {
        ReentrantLock lock = locks.computeIfAbsent(programId, key -> new ReentrantLock());
        lock.lock();
        LOGGER.info("program order local lock result, programId={}, locked={}", programId, true);
        try {
            return callback.get();
        } finally {
            lock.unlock();
            LOGGER.info("program order local lock released, programId={}", programId);
        }
    }
}
