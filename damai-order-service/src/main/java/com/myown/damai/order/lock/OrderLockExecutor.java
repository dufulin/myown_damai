package com.myown.damai.order.lock;

import java.util.function.Supplier;

/**
 * Executes order creation logic under a program-level lock.
 */
public interface OrderLockExecutor {

    /**
     * Runs the callback while holding the lock for one program.
     */
    <T> T executeWithProgramLock(Long programId, Supplier<T> callback);
}
