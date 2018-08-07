package com.springtale.ali;

/**
 * 用户信息缓存加载器
 * Created by wsy on 2018/8/8.
 */
public class UserInfoCacheLoader implements CacheLoader<String, UserInfo> {
    public UserInfo loadCache(String key) {
        // TODO: 2018/8/8 获取用户信息
        return new UserInfo();
    }
}
