package com.zhiyou.core.lock;

import com.google.common.collect.Maps;
import io.lettuce.core.api.StatefulRedisConnection;
import org.apache.commons.pool2.impl.GenericObjectPool;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RedisReentrantLock implements DistributedReentrantLock {

    private static final ThreadLocal<ConcurrentMap<String, LockData>> THREAD_DATA = ThreadLocal.withInitial(() -> Maps.newConcurrentMap());

    private GenericObjectPool<StatefulRedisConnection> lettucePool;

    private RedisLockInternals internals;

    private String lockId;


    public RedisReentrantLock(GenericObjectPool lettucePool, String lockId) {
        this.lettucePool = lettucePool;
        this.lockId = lockId;
        this.internals = new RedisLockInternals(lettucePool);
    }

    private static class LockData {
        final Thread owningThread;
        final String lockVal;
        final AtomicInteger lockCount = new AtomicInteger(1);

        private LockData(Thread owningThread, String lockVal) {
            this.owningThread = owningThread;
            this.lockVal = lockVal;
        }
    }

    @Override
    public boolean tryLock(long waitTime, TimeUnit unit) {
        ConcurrentMap<String, LockData> concurrentMap = THREAD_DATA.get();
        LockData lockData = concurrentMap.get(lockId);
        if (lockData != null) {
            lockData.lockCount.incrementAndGet();
            return true;
        }
        String lockVal = internals.tryRedisLock(lockId, waitTime, unit);
        if (lockVal != null) {
            LockData newLockData = new LockData(Thread.currentThread(), lockVal);
            concurrentMap.put(lockId, newLockData);
            THREAD_DATA.set(concurrentMap);
            return true;
        }
        return false;
    }

    @Override
    public void unlock() {
        ConcurrentMap<String, LockData> concurrentMap = THREAD_DATA.get();
        LockData lockData = concurrentMap.get(lockId);
        if (lockData == null) {
            throw new IllegalMonitorStateException("You do not own the lock: " + lockId);
        }
        int newLockCount = lockData.lockCount.decrementAndGet();
        if (newLockCount > 0) {
            return;
        }
        if (newLockCount < 0) {
            throw new IllegalMonitorStateException("Lock count has gone negative for lock: " + lockId);
        }
        try {
            internals.unlockRedisLock(lockId, lockData.lockVal);
        } finally {
            concurrentMap.remove(lockId);
        }
    }
}
