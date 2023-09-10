package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 注入工具类
    @Autowired
    private CacheClient cacheClient;

    //声明一个线程池，因为使用逻辑过期解决缓存击穿的方式需要新建一个线程来完成重构缓存
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 添加商户详情缓存
     * @param id
     * @return
     */
    /*@Override
    public Result queryById(Long id) {
        //先从Redis中查，这里的常量值是固定的前缀 + 店铺id
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //如果在redis中查询到了则转为Shop类型直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //在redis中没查询到则根据id去数据库中查
        Shop shop = getById(id);
        //在数据库中也查不到则返回一个错误信息或者返回空
        if (shop == null){
            return Result.fail("店铺不存在！！");
        }
        //在数据库中查到了则转为json字符串
        String jsonStr = JSONUtil.toJsonStr(shop);
        //并存入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonStr);
        //最终把查询到的商户信息返回给前端
        return Result.ok(shop);
    }*/

    /**
     * 添加商户详情缓存并设置超时时间,保持缓存与数据库数据更新一致
     * @param id
     * @return
     */
    /*@Override
    public Result queryById(Long id) {
        //先从Redis中查询商铺缓存，这里的常量值是固定的前缀 + 店铺id
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //如果查询到了商铺信息，则转为Shop类型直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //查询不到商铺信息则去数据库中查
        Shop shop = getById(id);
        //在数据库中查不到就返回一个错误信息或者返回空(根据业务需求)
        if (shop == null){
            return Result.fail("店铺不存在！！");
        }
        //在数据库中查到了则将商铺对象转为json字符串并存入redis
        String jsonStr = JSONUtil.toJsonStr(shop);
        //设置数据的有效时间
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //最终把查询到的商户信息返回给前端
        return Result.ok(shop);
    }*/

    /**
     *测试解决缓存穿透和缓存击穿
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // 测试缓存穿透
        //Shop shop = queryWithPassThrough(id);

        // 测试使用互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        // 测试使用逻辑过期的方式解决缓存击穿
        //Shop shop = queryWithLogicalExpire(id);

        // 测试工具类中的方法
        // 解决缓存穿透,this::getById是id2->getById(id2)的简写形式
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 互斥锁解决缓存击穿
        Shop shop = cacheClient.queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 逻辑过期解决缓存击穿
        // Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);
        if (shop == null) {
            return Result.fail("店铺不存在！！");
        }
        //最终把查询到的商户信息返回给前端
        return Result.ok(shop);
    }

    /**
     * 负责解决缓存穿透的方法
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        //先从Redis中查，这里的常量值是固定的前缀 + 店铺id
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //命中的不是空值则转为Shop类型直接返回,""和null以及"/t/n"都会判定为空
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //如果命中的是空字符串即我们缓存的空数据,返回一个错误信息
        if (shopJson != null) {
            return null;
        }
        //没有命中即shopJson等于null则去数据库中查
        Shop shop = getById(id);
        //查不到，则将空字符串写入Redis
        if (shop == null) {
            //设置空值的有效期(如2分钟)
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //查到了则转为json字符串
        String jsonStr = JSONUtil.toJsonStr(shop);
        //并存入redis，设置TTL
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonStr, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //最终把查询到的商户信息返回给前端
        return shop;
    }

    /**
     *使用互斥锁解决缓存击穿的方法
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        // 1.先从Redis中查，这里的常量值是固定的前缀 + 店铺id
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2.命中的不是空值则转为Shop类型直接返回,""和null以及"/t/n"都会判定为空
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //3.如果命中的是空字符串即我们缓存的空数据,返回一个错误信息
        if (shopJson != null) {
            return null;
        }
        // 4.没有命中则尝试根据锁的Id获取互斥锁(本质是插入key),实现缓存重构
        Shop shop = null;
        try {
            boolean isLock = tryLock(LOCK_SHOP_KEY + id);
            // 4.2 判断是否获取锁成功(插入key是否成功)
            if(!isLock){
                //4.3 获取锁失败(插入key失败),则休眠一段时间重新查询商铺缓存(递归)
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.4 获取锁成功(插入key成功),则根据id查询数据库
            shop = getById(id);
            // 由于本地查询数据库较快,模拟重建延时
            Thread.sleep(200);
            // 5.在数据库中查不到则将空值写入Redis
            if(shop == null){
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //6.shop不为null,表示在数据库中查到了,将shop对象转化为json字符串并写入redis(设置TTL)
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);

        }catch (Exception e){
            throw new RuntimeException(e);
        }
        finally {
            //7.不管前面是否会有异常，最终都必须释放锁
            unlock(LOCK_SHOP_KEY + id);
        }
        //最终把查询到的商户信息返回给前端
        return shop;
    }

    /*
    // 根据锁的Id尝试获取锁(本质是插入key),每一个商铺都有自己的锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        //避免在拆箱过程中返回null，我们这里使用了BooleanUtil工具类
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁(本质是删除key)
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
*/
    /**
     * 根据id更新商户详情数据时,先修改数据再删除缓存来解决双写问题(更新和删除通过事务去控制)
     * @param shop
     * @return
     */
    @Transactional
    public Result update(Shop shop) {
        //首先先判一下id是否为空
        if (shop.getId() == null){
            return Result.fail("店铺id不能为空！！");
        }
        //先修改数据库
        updateById(shop);
        //再删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    /**
     * 测试添加热点key
     * @param id
     * @param expirSeconds
     */
    public void saveShop2Redis(Long id, Long expirSeconds) {
        // 1.查询店铺数据
        Shop shop = getById(id);
        // 由于本地查询数据库较快,模拟重建延时
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 2.封装逻辑过期时间
        RedisData<Shop> redisData = new RedisData<>();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expirSeconds));
        // 3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 使用逻辑过期的方式解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire( Long id ) {
        // 1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 2.如果未命中即json等于null或空字符串直接返回空(说明我们没有导入对应的key)
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 3.如果命中即json有数据就需要反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        // 4.redisData.getData()的本质类型是JSONObject类型,这里不能直接强转为Shop类型
        JSONObject shopJson = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(shopJson, Shop.class);
        // 5.获取过期时间,判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())) {
            // 5.1.未过期，直接返回店铺信息
            return shop;
        }
        // 6.已过期，需要缓存重建
        // 6.1.获取互斥锁
        boolean isLock = tryLock(LOCK_SHOP_KEY + id);
        // 6.2.判断是否获取锁成功
        if (isLock){
            // 再次检测Redis缓存是否过期(双重检查),如果存在则无需重建缓存

            // 如果Redis缓存还是过期,开启独立线程,实现缓存重建(测试的时候可以休眠200ms),实际中缓存的逻辑过期时间设置为30分钟
            CACHE_REBUILD_EXECUTOR.submit( ()->{
                try{
                    this.saveShop2Redis(id,20L);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(LOCK_SHOP_KEY + id);
                }
            });
        }
        // 6.4.返回过期的商铺信息
        return shop;
    }

    // 根据锁的Id尝试获取锁(本质是插入key),每一个商铺都有自己的锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        //避免在拆箱过程中返回null，我们这里使用了BooleanUtil工具类
        return BooleanUtil.isTrue(flag);
    }

    // 释放锁(本质是删除key)
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
