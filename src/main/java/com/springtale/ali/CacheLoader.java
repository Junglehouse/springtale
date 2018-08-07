package com.springtale.ali;

/**
 * 缓存加载器
 * Created by wsy on 2018/8/8.
 */
public interface CacheLoader<K, V> {
    V loadCache(K key);
}
