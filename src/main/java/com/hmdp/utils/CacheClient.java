package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * @Author yunqing
 * @Date 2023/8/29 18:02
 * @PackageName:com.hmdp.utils
 * @ClassName: CacheClient
 * @Description: TODO
 * @Version 1.0
 */
@Slf4j
@Component
public class CacheClient {
    // 这里也可以使用自动注入
    private final StringRedisTemplate stringRedisTemplate;
    // 线程池用于在逻辑过期方式中开启独立线程时使用
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    // 基于构造方法自动注入,只有一个参数
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 将任意Java对象序列化为JSON字符串并存储在String类型的key中,并且可以设置TTL过期时间
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    /**
     *处理缓存击穿问题: 将任意Java对象序列化为JSON字符串并存储在String类型的key中,并且可以设置逻辑过期时间即给对象添加逻辑过期字段
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        //由于需要设置逻辑过期时间，所以我们需要用到RedisData
        RedisData redisData = new RedisData();
        //redisData的data就是传进来的value对象
        redisData.setData(value);
        //逻辑过期时间就是当前时间加上传进来的参数时间，用TimeUnit可以将时间转为秒随后与当前时间相加
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        //由于是逻辑过期所以不需要设置真实的过期时间，只存一下key和value就好了(value是ridisData类型)
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     *利用缓存空值的方式解决缓存穿透问题: 根据指定的key查询缓存中的数据,返回缓存数据时并反序列化为指定类型
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param timeUnit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        //先从Redis中查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //如果从Redsi缓存中查询到数据(JSON为空字符串或null)则转为对应的类型直接返回
        if (StrUtil.isNotBlank(json)) {
            // type就是xxx.class
            return JSONUtil.toBean(json, type);
        }
        //如果Redsi缓存中查询到的是空字符串直接返回空
        if (json != null) {
            return null;
        }
        //Redis缓存中查询不到数据(json等于null)则去数据库中查，查询逻辑是我们参数中注入的函数
        R r = dbFallback.apply(id);
        //在数据库中也查不到(r等于null)则将空字符串写入Redis
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //在数据库中查到了数据,将对象直接存入redis并设置TTL(set方法会帮我们把对象序列化成字符串)
        String jsonStr = JSONUtil.toJsonStr(r);
        this.set(key, r, time, timeUnit);
        //返回查询到的商户信息
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 利用逻辑过期字段解决缓存击穿问题: 根据指定的key查询缓存,并将缓存数据反序列化为指定类型
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param timeUnit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        //1. 从redis中查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2. 如果未命中(json等于null或空字符串)直接返回空(说明我们没有导入对应的key)
        if (StrUtil.isBlank(json)) {
            return null;
        }
        //3.如果命中即json有数据就需要反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //4.redisData.getData()的本质类型是JSONObject类型,不能直接强转为R类型
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断缓存数据是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 缓存数据未过期直接返回商铺信息
            return r;
        }
        //6.缓存数据过期尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean flag = tryLock(lockKey);
        //7.获取到了锁
        if (flag) {
            //8.开启独立线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库更新Redis缓存中的锁,查询逻辑使用我们参数中注入的函数
                    R tmp = dbFallback.apply(id);
                    // 将数据写入Redis(含逻辑过期时间)
                    this.setWithLogicExpire(key, tmp, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 9.返回过期的商铺信息
        return r;
    }

    /**
     * 利用互斥锁解决缓存击穿问题: 根据指定的Key查询缓存数据并将缓存数据反序列化为指定类型
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param timeUnit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        //先从Redis中查询缓存数据
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //如果从Redis中查到缓存数据(JSON不为空字符串或null)则转为R类型直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 如果从Redis查到的是空字符串直接返回null
        if (json != null) {
            return null;
        }
            // Redis缓存中查询不到数据(json等于null)则获取互斥锁然后从数据库中查询数据实现缓存重建
        R r = null;
        String lockKey = LOCK_SHOP_KEY + id;
        try {
            // 获取互斥锁
            boolean flag = tryLock(lockKey);
            if (!flag) {
                // 获取锁失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, timeUnit);
            }
            // 获取锁成功根据id查询数据库,查询逻辑是我们参数中注入的函数
            r = dbFallback.apply(id);
            // 数据库中也查不到数据，则将空值写入Redis并返回null(店铺不存在)
            if (r == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //查到则将数据存入redis并设置TTL
            this.set(key, r, time, timeUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unlock(lockKey);
        }
        // 返回查到的对象信息
        return r;
    }

    
}
