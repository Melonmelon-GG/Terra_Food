package com.dayan.food.service;

import com.dayan.food.entity.vo.FoodCommentVO;

import java.util.List;

public interface FoodCommentService {

    List<FoodCommentVO> list(Long foodId);

    FoodCommentVO create(Long foodId, String content, String username);
}
