package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    // 线程池
    private ExecutorService es = Executors.newFixedThreadPool(500);

    // 向Redis中添加热点key
    @Test
    public void test(){
        shopService.saveShop2Redis(1L,1000L);
        shopService.saveShop2Redis(2L,100L);
    }

    // 生成30000个ID
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        // 设置每个线程的任务
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            // 让分线程和countDown方法内部的变量绑定,执行完一个分线程变量减少1，当分线程全部走完后变量变成0
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        // 设置300个线程,提交任务
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        // 让main线程阻塞,等分线程执行完即CountDownLatch内部维护的变量变为0时才执行主线程
        latch.await();
        long end = System.currentTimeMillis();
        // 统计出来的时间也就是所有分线程执行完后的时间
        System.out.println("time = " + (end - begin));
    }

}
