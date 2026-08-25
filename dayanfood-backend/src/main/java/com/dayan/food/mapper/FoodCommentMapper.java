package com.dayan.food.mapper;

import com.dayan.food.entity.po.FoodComment;

import java.util.List;

public interface FoodCommentMapper {

    List<FoodComment> findByFoodId(Long foodId);

    int insert(FoodComment comment);
}
