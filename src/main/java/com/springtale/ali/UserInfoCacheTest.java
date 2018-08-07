package com.springtale.ali;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by wsy on 2018/8/8.
 */
public class UserInfoCacheTest {
    //: TODO 可自行定义需要的变量
    private final long expireTime = 30 * 60 * 1000;
    private final int concurrentLevel = 8;
    private final SimpleCache<String, UserInfo> cache
            = new SimpleCache<String, UserInfo>(expireTime, concurrentLevel, new UserInfoCacheLoader());

    /**
     * 初始化用户信息缓存
     */
    public void initUserInfoCache() {
        //: TODO 完成此处的代码
        Map<String, UserInfo> initUserInfoMap = new HashMap<String, UserInfo>();
        for (Map.Entry<String, UserInfo> entry : initUserInfoMap.entrySet()) {
            cache.addOrRefresh(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 根据id从缓存中获取用户信息
     */
    public UserInfo getUserInfoFromCacheById(String id) {
        //: TODO 完成此处的代码
        return cache.get(id);
    }

    /**
     * 根据id更新缓存用户信息
     */
    public void updateUserInfoCache(String id) {
        //: TODO 完成此处的代码
        cache.addOrRefresh(id);
    }
}
