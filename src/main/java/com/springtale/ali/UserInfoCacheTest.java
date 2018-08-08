package com.springtale.ali;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by wsy on 2018/8/8.
 */
public class UserInfoCacheTest {
    //: TODO 可自行定义需要的变量
    private final long expireTime = 30 * 60 * 1000;
    private final int concurrentLevel = 8;
    private final SimpleCache<String, UserInfo> cache = new SimpleCache.Builder<String, UserInfo>()
            .cacheLoader(new UserInfoCacheLoader()) // 自动缓存装载器
            .concurrentLevel(concurrentLevel) // 并行度
            .expireTime(expireTime) // 超时时间
            .initialCapacity(64).build(); // 初始容量

    /**
     * 初始化用户信息缓存
     */
    public void initUserInfoCache() {
        //: TODO 完成此处的代码
        Map<String, UserInfo> initUserInfoMap = new HashMap<String, UserInfo>();
        for (Map.Entry<String, UserInfo> entry : initUserInfoMap.entrySet()) {
            cache.put(entry.getKey(), entry.getValue());
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

    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 20, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10000000));
        ThreadPoolExecutor executor1 = new ThreadPoolExecutor(10, 20, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10000000));
        final Random random = new Random();
        final UserInfoCacheTest test = new UserInfoCacheTest();
        long start = System.currentTimeMillis();
        System.out.println("start time:"+start);
        for (int i=0; i<10000000; i++) {
            executor.submit(() -> {
                String id = String.valueOf(random.nextInt(10000000));
                test.getUserInfoFromCacheById(id);
            });
        }

        System.out.println(test.cache.getSize());
        long end = System.currentTimeMillis();
        System.out.println("end time:"+(end-start));
        executor.shutdown();
        while (executor.isShutdown()) {
            System.out.println(executor.getActiveCount() + "###" + executor.getCompletedTaskCount() + "###" + executor.getTaskCount());
            System.out.println(executor.getQueue().size());
        }
        for (int i=0; i<10000000; i++) {
            executor1.submit(() -> {
                String id = String.valueOf(random.nextInt(10000000));
                test.getUserInfoFromCacheById(id);
            });
        }
        System.out.println(test.cache.getSize());
        System.out.println("end time:"+(System.currentTimeMillis()-end));
        executor1.shutdown();

    }
}
