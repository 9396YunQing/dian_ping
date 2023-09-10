package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @Author yunqing
 * @Date 2023/8/30 9:19
 * @PackageName:com.hmdp.utils
 * @ClassName: RedisIdWorker
 * @Description: TODO
 * @Version 1.0
 */
@Component
public class RedisIdWorker {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    //设置起始时间的时间戳，如2023.08.20 00:00:00
    public static final Long BEGIN_TIMESTAMP = 1692489600L;
    //序列号长度
    public static final Long COUNT_BIT = 32L;

    /**
     * 生成全局唯一ID
     * @param keyPrefix
     * @return
     */
    public long nextId(String keyPrefix){
        //1. 生成当前时间的时间戳
        LocalDateTime now = LocalDateTime.now();
        long currentSecond = now.toEpochSecond(ZoneOffset.UTC);
        // 生成订单时间和起始时间的起始时间差
        long timeStamp = currentSecond - BEGIN_TIMESTAMP;
        //2. 使用Rerdis生成序列号,以日期精确到天作为key然后自增长,不同天对应不同的key也便于统计
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("inc:"+keyPrefix+":"+date);
        //3. 拼接订单ID并返回，简单位运算时间戳向左移动32位,空出来的位数由序列号填充(采用或运算)
        return timeStamp << COUNT_BIT | count;
    }

    // 获取某个时间对应的时间戳
    public static void main(String[] args) {
        //设置起始时间
        LocalDateTime tmp = LocalDateTime.of(2023, 8, 20, 0, 0, 0);
        //指定时区得到指定时间对应的时间戳
        System.out.println(tmp.toEpochSecond(ZoneOffset.UTC));
        //结果为1640995200L
    }
}
