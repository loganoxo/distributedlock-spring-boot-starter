package com.zhiyou.core;

import com.zhiyou.core.lock.Callback;
import com.zhiyou.core.lock.RedisDistributedLockTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
public class ApplicationTests {

    @Autowired
    private RedisDistributedLockTemplate lockTemplate;

    @Test
    public void contextLoads() {
        lockTemplate.execute("订单流水号1", 50000, new Callback() {
            @Override
            public Object onGetLock() throws InterruptedException {
                //TODO 获得锁后要做的事
                log.info("生成订单流水号1");


                lockTemplate.execute("订单流水号1", 50000, new Callback() {
                    @Override
                    public Object onGetLock() throws InterruptedException {
                        //TODO 获得锁后要做的事
                        log.info("生成订单流水号2");
                        return null;
                    }

                    @Override
                    public Object onTimeout() throws InterruptedException {
                        //TODO 获得锁超时后要做的事
                        return null;
                    }
                });
                return null;
            }

            @Override
            public Object onTimeout() throws InterruptedException {
                //TODO 获得锁超时后要做的事
                return null;
            }
        });

    }

    @Test
    public void contextLoads2() {


    }
}
