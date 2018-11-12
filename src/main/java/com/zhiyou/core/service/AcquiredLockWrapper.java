package com.zhiyou.core.service;

/**
 * Created by QinHe on 9/4/2017
 */
public interface AcquiredLockWrapper<T> {
    T invokeAfterLockAcquire() throws Exception;
}
