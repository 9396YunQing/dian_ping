package com.hmdp.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author yunqing
 * @Date 2023/8/29 13:07
 * @PackageName:com.hmdp.entity
 * @ClassName: RedisData
 * @Description: TODO
 * @Version 1.0
 */
@Data
public class RedisData<T> {
        private LocalDateTime expireTime;
        private T data;
        //private Object data;

}
