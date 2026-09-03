package com.dayan.food.service;

/**
 * 登录初始化阶段的缓存预热。失败时必须允许用户继续登录。
 */
public interface LoginPreloadService {

    void preload(String username);
}
