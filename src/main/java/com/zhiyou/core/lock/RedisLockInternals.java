package com.zhiyou.core.lock;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.impl.GenericObjectPool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

@Slf4j
class RedisLockInternals {

    private GenericObjectPool<StatefulRedisConnection> lettucePool;

    /**
     * 重试等待时间
     */
    private final int retryAwait = 1000;

    /**
     * redis过期时间
     */
    private final int lockTimeout = 100000;


    RedisLockInternals(GenericObjectPool lettucePool) {
        this.lettucePool = lettucePool;
    }

    String tryRedisLock(String lockId, long waitTime, TimeUnit unit) {
        final AtomicInteger tryCount = new AtomicInteger(1);
        final long startMillis = System.currentTimeMillis();
        final Long millisToWait = (unit != null) ? unit.toMillis(waitTime) : null;
        String lockValue = null;
        while (lockValue == null) {
            lockValue = createRedisKey(lockId);
            if (lockValue != null) {
                break;
            }
            if (System.currentTimeMillis() - startMillis - retryAwait > millisToWait) {
                break;
            }
            log.info("lockId:{},tryRedisLock_times:{}", lockId, tryCount.get());
            tryCount.incrementAndGet();
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(retryAwait));
        }
        return lockValue;
    }

    private String createRedisKey(String lockId) {

        StatefulRedisConnection connection = null;
        try {
            String value = lockId + "_" + randomId(4);
            connection = lettucePool.borrowObject();
            String luaScript = ""
                    + "\nlocal r = tonumber(redis.call('SETNX', KEYS[1],ARGV[1]));"
                    + "\nredis.call('PEXPIRE',KEYS[1],ARGV[2]);"
                    + "\nreturn r";
            List<String> keys = new ArrayList<>();
            keys.add(lockId);
            List<String> args = new ArrayList<>();
            args.add(value);
            args.add(lockTimeout + "");
            RedisAsyncCommands redisAsyncCommands = connection.async();
            Long ret = (Long) redisAsyncCommands.eval(luaScript, ScriptOutputType.INTEGER, keys.toArray(), args.toArray()).get();
            if (new Long(1).equals(ret)) {
                return value;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
        return null;
    }

    void unlockRedisLock(String key, String value) {
        StatefulRedisConnection connection = null;
        try {
            connection = lettucePool.borrowObject();
            String luaScript = ""
                    + "\nlocal v = redis.call('GET', KEYS[1]);"
                    + "\nlocal r= 0;"
                    + "\nif v == ARGV[1] then"
                    + "\nr =redis.call('DEL',KEYS[1]);"
                    + "\nend"
                    + "\nreturn r";
            List<String> keys = new ArrayList<>();
            keys.add(key);
            List<String> args = new ArrayList<>();
            args.add(value);
            RedisAsyncCommands redisAsyncCommands = connection.async();
            redisAsyncCommands.eval(luaScript, ScriptOutputType.INTEGER, keys.toArray(), args.toArray()).get();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private final static char[] digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8',
            '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l',
            'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y',
            'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L',
            'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y',
            'Z'};

    private String randomId(int size) {

        char[] cs = new char[size];
        for (int i = 0; i < cs.length; i++) {
            cs[i] = digits[ThreadLocalRandom.current().nextInt(digits.length)];
        }
        return new String(cs);
    }

    public static void main(String[] args) {
        System.out.println(System.currentTimeMillis());
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10000));
        System.out.println(System.currentTimeMillis());
    }
}
