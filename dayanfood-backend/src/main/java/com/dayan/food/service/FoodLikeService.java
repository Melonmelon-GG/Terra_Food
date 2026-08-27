package com.dayan.food.service;

import com.dayan.food.entity.vo.FoodLikeStatusVO;

public interface FoodLikeService {

    FoodLikeStatusVO status(Long foodId, String username);

    FoodLikeStatusVO like(Long foodId, String username);

    FoodLikeStatusVO unlike(Long foodId, String username);
}