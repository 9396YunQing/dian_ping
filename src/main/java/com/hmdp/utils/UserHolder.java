package com.hmdp.utils;


import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
/**
 * 将用户的信息保存到UserDto类中,隐藏用户的敏感信息
 */
public class UserHolder {
    /**
     * 不隐藏用户的敏感信息,将用户信息直接封装到User对象中
     *//*
public class UserHolder {
    private static final ThreadLocal<User> tl = new ThreadLocal<>();

    public static void saveUser(User user){
        tl.set(user);
    }

    public static User getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
*/
    /**
     * 隐藏用户的信息
     */
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();
    public static void saveUser(UserDTO user){
        tl.set(user);
    }
    public static UserDTO getUser(){
        return tl.get();
    }
    public static void removeUser(){
        tl.remove();
    }
}


