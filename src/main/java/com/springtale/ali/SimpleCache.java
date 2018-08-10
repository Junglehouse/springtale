package com.springtale.ali;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 通用本地缓存
 * Created by wsy on 2018/8/8.
 */
public class SimpleCache<K, V> {
    private ConcurrentHashMap<K, CacheEntry<V>> innerCacheMap;
    // TODO: 2018/8/8 0008 判断缓存满了，并移除时有并发问题
    /**
     * 1、将缓存数量控制分割到每个分段内，这样每个分段控制数量
     * 2、将缓存的新增与更新操作分开，数量控制只需在新增时判断
     */
    // 最小并发数
    private static final int MIN_CONCURRENT_LEVEL = 1;
    // 最大并发数
    private static final int MAX_CONCURRENT_LEVEL = Integer.MAX_VALUE>>1 + 1;
    // 默认超时时间30分钟
    private static final long DEFAULT_EXPIRE_TIME = 30 * 60 * 1000;
    // 并发度
    private int concurrentLevel;

    // 初始容量
    private int initialCapacity;

    // 缓存数量控制
    private long cacheSizeCtl;

    // 移除选取样本数量
    private int evictSampleSize;

    // 过期时间
    private long expireTime;

    // 并发分段锁
    private ReentrantLock[] locks;

    // 缓存加载器
    private CacheLoader<K, V> cacheLoader;

    // 初始化方法
    public void init() {
        if (expireTime < 0) {
            expireTime = DEFAULT_EXPIRE_TIME;
        }
        // 保证concurrentLevel为2的n次幂
        if (concurrentLevel <= MIN_CONCURRENT_LEVEL) {
            concurrentLevel = MIN_CONCURRENT_LEVEL;
        }else if (concurrentLevel >= MAX_CONCURRENT_LEVEL) {
            concurrentLevel = MAX_CONCURRENT_LEVEL;
        }else {
            int powerTwo = 1;
            while (powerTwo < concurrentLevel) {
                powerTwo = powerTwo << 1;
            }
            concurrentLevel = powerTwo;
        }

        innerCacheMap = new ConcurrentHashMap<>(initialCapacity);

        // 初始化分段锁
        locks = new ReentrantLock[concurrentLevel];
        for (int i=0; i<concurrentLevel; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    public static class Builder<K, V> {
        // 默认16并发
        private int concurrentLevel = 16;

        // 初始容量，默认64
        private int initialCapacity = 64;

        // 缓存数量控制，默认1000
        private long cacheSizeCtl = 1000;

        // 移除选取样本数量，默认50个
        private int evictSampleSize = 50;

        // 默认30分钟过期
        private long expireTime = 30 * 60 * 1000;

        // 缓存加载器
        private CacheLoader<K, V> cacheLoader;

        public Builder<K, V> concurrentLevel(int concurrentLevel) {
            this.concurrentLevel = concurrentLevel;
            return this;
        }

        public Builder<K, V> initialCapacity(int initialCapacity) {
            this.initialCapacity = initialCapacity;
            return this;
        }

        public Builder<K, V> cacheSizeCtl(long cacheSizeCtl) {
            this.cacheSizeCtl = cacheSizeCtl;
            return this;
        }

        public Builder<K, V> evictSampleSize(int evictSampleSize) {
            this.evictSampleSize = evictSampleSize;
            return this;
        }

        public Builder<K, V> expireTime(long expireTime) {
            this.expireTime = expireTime;
            return this;
        }

        public Builder<K, V> cacheLoader(CacheLoader<K, V> cacheLoader) {
            this.cacheLoader = cacheLoader;
            return this;
        }

        public SimpleCache<K, V> build() {
            SimpleCache<K, V> simpleCache = new SimpleCache<K, V>();
            simpleCache.concurrentLevel = this.concurrentLevel;
            simpleCache.initialCapacity = this.initialCapacity;
            simpleCache.cacheSizeCtl = this.cacheSizeCtl;
            simpleCache.evictSampleSize = this.evictSampleSize;
            simpleCache.expireTime = this.expireTime;
            simpleCache.cacheLoader = this.cacheLoader;
            simpleCache.init();
            return simpleCache;
        }
    }

    /**
     * 新增或更新缓存
     * @param key
     * @param value
     * @return
     */
    public V put(K key, V value) {
        V result = loadOrRefresh(key, value);
        lockedPostProcess(key);
        return result;
    }

    /**
     * 新增或更新缓存
     * @param key
     * @return
     */
    public V addOrRefresh(K key) {
        V result = loadOrRefresh(key);
        lockedPostProcess(key);
        return result;
    }

    /**
     * 线程安全的获取
     * @param key
     * @return
     */
    public V get(K key) {
        // 先尝试从缓存中获取
        V result = getUnexpiredValue(key);
        if (null == result) {
            // 不在缓存中则进行加载
            result = lockedLoad(key);
        }
        return result;
    }

    /**
     * 删除指定缓存
     * @param key
     * @return
     */
    public V remove(K key) {
        return innerCacheMap.remove(key).getCacheObj();
    }

    /**
     * 清空所有缓存
     */
    public void reCache() {
        innerCacheMap = new ConcurrentHashMap<>(initialCapacity);
    }



    private V loadOrRefresh(K key) {
        return loadOrRefresh(key, null);
    }

    private V loadOrRefresh(K key, V value) {
        if (null == value) {
            value = loadCache(key);
        }
        CacheEntry<V> cacheEntry = new CacheEntry<>(System.currentTimeMillis()+expireTime, value);
        innerCacheMap.put(key, cacheEntry);
        return value;
    }

    /**
     * 增加或更新的后置处理，用于维持缓存数量
     * @param key
     */
    private void lockedPostProcess(K key) {
        ReentrantLock lock = lockOf(key);
        lock.lock();
        try {
            if (shouldEvict()) {
                approxiRemoveCache(evictSampleSize);
            }
        }finally {
            lock.unlock();
        }
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
                result = loadOrRefresh(key);
                if (shouldEvict()) {
                    // 清理缓存
                    approxiRemoveCache(evictSampleSize);
                }
            }
            return result;
        }finally {
            lock.unlock();
        }
    }

    /**
     * 加载缓存信息
     * @param key
     * @return
     */
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

    /**
     * 随机选取一定的样本，移除其中时间最早的缓存
     * @param sampleSize 样本数量
     * @return
     */
    private V approxiRemoveCache(int sampleSize) {
        sampleSize = sampleSize < 1 ? 1 : sampleSize;
        Map.Entry<K, CacheEntry<V>> removeEntry = null;
        for (Map.Entry<K, CacheEntry<V>> entry : innerCacheMap.entrySet()) {
            if (0 < sampleSize--) {
                if (null == removeEntry) {
                    removeEntry = entry;
                }else {
                    removeEntry = removeEntry.getValue().getExpireAt() > entry.getValue().getExpireAt() ? entry : removeEntry;
                }
            }else {
                break;
            }
        }
        if (null == removeEntry) {
            return null;
        }
        return innerCacheMap.remove(removeEntry.getKey()).getCacheObj();
    }

    private boolean shouldEvict() {
        if (cacheSizeCtl > 0) {
            return this.cacheSizeCtl < getSize();
        }
        return false;
    }

    public long getSize() {
        return innerCacheMap.mappingCount();
    }

    public CacheLoader<K, V> getCacheLoader() {
        return cacheLoader;
    }

    public void setCacheLoader(CacheLoader<K, V> cacheLoader) {
        this.cacheLoader = cacheLoader;
    }

    /**
     * 封装过期时间、缓存实体
     * @param <E>
     */
    static class CacheEntry<E> {
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
