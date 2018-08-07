package com.springtale.ali;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 通用本地缓存
 * 1.线程安全
 * 2.不设置缓存加载器则只保存key与时间
 * 3.expireTime默认设置30分钟，设置时间不大于0时使用默认值
 * 4.concurrentLevel需设置为2的n次幂，表示并发度
 * 5.本缓存未设置缓存上限，由用户根据业务需求进行控制
 * Created by wsy on 2018/8/8.
 */
public class SimpleCache<K, V> {
    private final ConcurrentHashMap<K, CacheEntry<V>> innerCacheMap = new ConcurrentHashMap<K, CacheEntry<V>>();

    // 最小并发数
    private static final int MIN_CONCURRENT_LEVEL = 1;
    // 最大并发数
    private static final int MAX_CONCURRENT_LEVEL = Integer.MAX_VALUE>>1 + 1;
    // 默认8个并发
    private int concurrentLevel = 8;

    // 默认30分钟过期
    private long expireTime = 30 * 60 * 1000;

    // 并发分段锁
    private ReentrantLock[] locks;

    // 缓存加载器
    private CacheLoader<K, V> cacheLoader;

    public SimpleCache(long expireTime, int concurrentLevel, CacheLoader<K, V> cacheLoader) {
        this(expireTime, concurrentLevel);
        this.cacheLoader = cacheLoader;
    }

    public SimpleCache(long expireTime, int concurrentLevel) {
        init(expireTime, concurrentLevel);
    }

    public void init(long expireTime, int concurrentLevel) {
        if (expireTime > 0) {
            this.expireTime = expireTime;
        }
        // 保证concurrentLevel为2的n次幂
        if (concurrentLevel <= MIN_CONCURRENT_LEVEL) {
            this.concurrentLevel = MIN_CONCURRENT_LEVEL;
        }else if (concurrentLevel >= MAX_CONCURRENT_LEVEL) {
            this.concurrentLevel = MAX_CONCURRENT_LEVEL;
        }else {
            int powerTwo = 1;
            while (powerTwo < concurrentLevel) {
                powerTwo = powerTwo << 1;
            }
            this.concurrentLevel = powerTwo;
        }

        // 初始化分段锁
        this.locks = new ReentrantLock[this.concurrentLevel];
        for (int i=0; i<this.concurrentLevel; i++) {
            this.locks[i] = new ReentrantLock();
        }
    }

    /**
     * 新增或更新缓存
     * @param key
     * @param value
     * @return
     */
    public V addOrRefresh(K key, V value) {
        CacheEntry<V> cacheEntry = new CacheEntry<V>(System.currentTimeMillis()+expireTime, value);
        innerCacheMap.put(key, cacheEntry);
        return value;
    }

    /**
     * 新增或更新缓存
     * @param key
     * @return
     */
    public V addOrRefresh(K key) {
        V cache = loadCache(key);
        CacheEntry<V> cacheEntry = new CacheEntry<V>(System.currentTimeMillis()+expireTime, cache);
        innerCacheMap.put(key, cacheEntry);
        return cache;
    }

    /**
     * 线程安全的获取
     * @param key
     * @return
     */
    public V get(K key) {
        V result = getUnexpiredValue(key);
        if (null == result) {
            result = lockedLoad(key);
        }
        return result;
    }

    /**
     * 线程安全加载缓存
     * @param key
     * @return
     */
    private V lockedLoad(K key) {
        ReentrantLock lock = lockOf(key);
        lock.lock();
        try {
            // 再次判断，避免多次加载
            V result = getUnexpiredValue(key);
            if (null == result) {
                result = addOrRefresh(key);
            }
            return result;
        }finally {
            lock.unlock();
        }
    }

    // 加载缓存信息
    private V loadCache(K key) {
        if (null != cacheLoader) {
            return cacheLoader.loadCache(key);
        }
        return null;
    }

    /**
     * 简单获取分段锁
     * @param key
     * @return
     */
    private ReentrantLock lockOf(K key) {
        int hash = key.hashCode();
        return locks[hash & (concurrentLevel-1)];
    }

    /**
     * 获取未过期的缓存
     * @param key
     * @return
     */
    private V getUnexpiredValue(K key) {
        CacheEntry<V> result = innerCacheMap.get(key);
        if (null != result && !result.isExpire()) {
            return result.getCacheObj();
        }
        return null;
    }

    public CacheLoader<K, V> getCacheLoader() {
        return cacheLoader;
    }

    public void setCacheLoader(CacheLoader<K, V> cacheLoader) {
        this.cacheLoader = cacheLoader;
    }

    /**
     * 封装时间、缓存实体
     * @param <E>
     */
    private static class CacheEntry<E> {
        private long expireAt;
        private E cacheObj;

        public CacheEntry(long expireAt, E cacheObj) {
            this.expireAt = expireAt;
            this.cacheObj = cacheObj;
        }

        /**
         * 判断缓存是否过期
         * @return true:过期，false:有效
         */
        public boolean isExpire() {
            return expireAt < System.currentTimeMillis();
        }

        public long getExpireAt() {
            return expireAt;
        }

        public void setExpireAt(long expireAt) {
            this.expireAt = expireAt;
        }

        public E getCacheObj() {
            return cacheObj;
        }

        public void setCacheObj(E cacheObj) {
            this.cacheObj = cacheObj;
        }
    }


}
