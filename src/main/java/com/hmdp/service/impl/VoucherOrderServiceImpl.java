package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;

    /**
     * 使用乐观锁解决超卖问题
     * @param voucherId
     * @return
     */
    /*@Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        LambdaQueryWrapper<SeckillVoucher> queryWrapper = new LambdaQueryWrapper<>();
        //1. 查询优惠券的基本信息
        queryWrapper.eq(SeckillVoucher::getVoucherId, voucherId);
        SeckillVoucher seckillVoucher = seckillVoucherService.getOne(queryWrapper);
        //2. 判断秒杀时间是否开始
        if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())) {
            return Result.fail("秒杀还未开始，请耐心等待");
        }
        //3. 判断秒杀时间是否结束
        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
            return Result.fail("秒杀已经结束！");
        }
        //4. 判断库存是否充足
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("优惠券已被抢光了哦，下次记得手速快点");
        }
        //5. 扣减库存,,此时会出现超卖现象
        // boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId).update();
        // 使用stock>0充当判断条件,在扣减库存时只要判断是否有剩余优惠券,即只要数据库中的库存大于0都能顺利完成扣减库存操作
        boolean success = seckillVoucherService.update()
                .setSql("stock= stock -1")
                .eq("voucher_id", voucherId).gt("stock",0).update(); //where voucher_id = ? and stock > 0
        if (!success) {
            return Result.fail("库存不足");
        }
        //6. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1 设置订单id
        long orderId = redisIdWorker.nextId("order");
        //6.2 设置用户id
        Long id = UserHolder.getUser().getId();
        //6.3 设置代金券id
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(id);
        //7. 将订单数据保存到表中
        save(voucherOrder);
        //8. 返回订单id
        return Result.ok(orderId);
    }*/

    /**
     * 使用乐观锁解决超卖问题,使用悲观锁解决一人一单
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        LambdaQueryWrapper<SeckillVoucher> queryWrapper = new LambdaQueryWrapper<>();
        //1. 查询优惠券的基本信息
        queryWrapper.eq(SeckillVoucher::getVoucherId, voucherId);
        SeckillVoucher seckillVoucher = seckillVoucherService.getOne(queryWrapper);
        //2. 判断秒杀时间是否开始
        if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())) {
            return Result.fail("秒杀还未开始，请耐心等待");
        }
        //3. 判断秒杀时间是否结束
        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
            return Result.fail("秒杀已经结束！");
        }
        //4. 判断库存是否充足
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("优惠券已被抢光了哦，下次记得手速快点");
        }
       /* this不具有事务功能
       // 获取用户id
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            return createVoucherOrder(voucherId);
        }*/

        /**
         * 使用VoucherOrderServiceImpl的代理对象(具有事务功能)
         */
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            // 获取具有事务功能的代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    /**
     * 封装一人一单的逻辑代码
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId){
        // 5.根据用户id查询用户对应的订单是否存在
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("voucher_id", voucherId).eq("user_id", userId).count();
        if (count > 0) {
            // 用户已经下过单
            return Result.fail("您已经抢过优惠券了哦");
        }
        // 6.使用stock>0充当判断条件,在扣减库存时只要判断是否有剩余优惠券,即只要数据库中的库存大于0都能顺利完成扣减库存操作
        boolean success = seckillVoucherService.update()
                .setSql("stock= stock -1")
                .eq("voucher_id", voucherId).gt("stock",0).update(); //where voucher_id = ? and stock > 0
        if (!success) {
            return Result.fail("库存不足");
        }
        //7. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //7.1 设置订单id
        long orderId = redisIdWorker.nextId("order");
        //7.2 设置用户id
        Long id = UserHolder.getUser().getId();
        //7.3 设置代金券id
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(id);
        //8. 将订单数据保存到表中
        save(voucherOrder);
        //9. 返回订单id
        return Result.ok(orderId);
    }
}
