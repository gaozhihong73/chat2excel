package com.red.common.service;

import org.springframework.stereotype.Service;

/**
 * 分布式锁服务
 */
public interface DistributeLockService {

    /**
     * 获取锁
     * @param lockKey
     * @param lockValue
     * @param expireTime
     * @return
     */
    boolean tryLock(String lockKey, String lockValue, long expireTime);

    /**
     * 释放锁
     * @param lockKey
     * @param lockValue
     * @return
     */
    boolean releaseLock(String lockKey, String lockValue);
}
