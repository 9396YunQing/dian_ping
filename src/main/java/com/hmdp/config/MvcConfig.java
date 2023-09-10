package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
// import com.hmdp.utils.RefreshTokenInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @Author yunqing
 * @Date 2023/8/14 11:22
 * @PackageName:com.hmdp.config
 * @ClassName: MvcConfig
 * @Description: TODO
 * @Version 1.0
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * LoginInterceptor拦截器只能拦截需要登陆校验的路径
     * @param registry
     */
    /*@Override
    public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new LoginInterceptor(stringRedisTemplate))
                    .excludePathPatterns(
                            "/user/code",
                            "/user/login",
                            "/blog/hot",
                            "/shop/**",
                            "/shop-type/**",
                            "/upload/**",
                            "/voucher/**"
                    );
    }*/


    /**
     * LoginInterceptor拦截器拦截登陆校验路径,负责判断用户信息是否存在
     * RefreshTokenInterceptor拦截器可以拦截所有路径: 负责基于taken获取用户信息并保存用户的信息到ThreadLocal当中同时刷新令牌有效期
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**"
                ).order(1);
        //RefreshTokenInterceptor是我们手动new出来的,不能自动注入StringRedisTemplate
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
    }

}
