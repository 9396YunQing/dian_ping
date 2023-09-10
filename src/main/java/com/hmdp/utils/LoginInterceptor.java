package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Author yunqing
 * @Date 2023/8/14 11:11
 * @PackageName:com.hmdp.utils
 * @ClassName: LoginInterceptor
 * @Description: TODO
 * @Version 1.0
 */
public class LoginInterceptor implements HandlerInterceptor {
   /* *
     * 获取session中隐藏的用户信息
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    /*@Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取session
        HttpSession session = request.getSession();
        //2.获取session中的用户,将从session中获取的用户信息隐藏
        UserDTO user = (UserDTO) session.getAttribute("user");

        //3.判断用户是否存在
        if(user == null){
            //4.不存在则拦截并返回401状态码
            response.setStatus(401);
            return false;
        }
        //5.若存在保存用户的隐藏信息到Threadlocal
        UserHolder.saveUser(user);
        //6.放行
        return true;
    }
*/

   /* *
     * 从redis中获取用户信息同时刷新令牌有效期
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
   /*
   //这里不能自动装配，因为LoginInterceptor是我们手动在WebConfig里new出来的并不受容器的管理
   private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1. 获取请求头中的token
        String token = request.getHeader("authorization");
        //2. 如果token是空表示未登录拦截
        if (StrUtil.isBlank(token)) {
            response.setStatus(401);
            return false;
        }
        //3. 基于token获取Redis中的用户数据,获取一个Map集合
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        //4. 判断Map集合中有没有元素，没有则拦截
        if (userMap.isEmpty()) {
            response.setStatus(401);
            return false;
        }
        //5. 将查询到的Hash数据转化为UserDto对象,fasle表示不忽略转化时的错误
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //6. 将用户信息保存到ThreadLocal
        UserHolder.saveUser(userDTO);
        //7. 刷新token有效期，这里的存活时间根据需要自己设置，这里的常量值我改为了30分钟
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //8. 放行
        return true;
    }*/

    /**
     * 只负责判断ThreadLocal中的用户信息是否存在
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.判断ThreadLocal中是否有用户
        if (UserHolder.getUser() == null) {
            // 不存在则拦截并设置状态码
            response.setStatus(401);
            return false;
        }
        // 用户存在则放行
        return true;
    }
}
