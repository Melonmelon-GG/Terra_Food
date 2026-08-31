package com.dayan.food.service;

import com.dayan.food.entity.vo.UserPublicVO;

public interface UserPublicService {

    UserPublicVO getProfile(Long userId);
}