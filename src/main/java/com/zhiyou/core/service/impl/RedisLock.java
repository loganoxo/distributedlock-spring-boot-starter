package com.zhiyou.core.service.impl;

import com.zhiyou.core.exception.UnableToAcquireLockException;
import com.zhiyou.core.service.AcquiredLockWrapper;
import com.zhiyou.core.service.DistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Created by QinHe on 9/4/2017
 */
@Slf4j
@Component
public class RedisLock implements DistributedLock {

    private final static String LOCKER_PREFIX = "lock:";

    private final RedissonClient redisson;

    @Autowired
    public RedisLock(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public <T> T lock(String resourceName, AcquiredLockWrapper<T> worker) throws InterruptedException, UnableToAcquireLockException, Exception {
        return lock(resourceName, worker, 100);
    }

    @Override
    public <T> T lock(String resourceName, AcquiredLockWrapper<T> worker, int lockTime) throws UnableToAcquireLockException, Exception {
        log.info("redisLock, tryGetLock=" + LOCKER_PREFIX + resourceName);
        RLock lock = redisson.getLock(LOCKER_PREFIX + resourceName);
        log.info("redisLock, getLockSuccess=" + LOCKER_PREFIX + resourceName);
        // Wait for 100 seconds seconds and automatically unlock it after lockTime seconds
        boolean success = lock.tryLock(100, lockTime, TimeUnit.SECONDS);
        log.info("redisLock, tryLock=" + LOCKER_PREFIX + resourceName + ", result" + success);
        if (success) {
            try {
                return worker.invokeAfterLockAcquire();
            } finally {
                log.info("redisLock, tryLock=" + LOCKER_PREFIX + resourceName + ", unlock");
                lock.unlock();
            }
        }
        throw new UnableToAcquireLockException();
    }
}