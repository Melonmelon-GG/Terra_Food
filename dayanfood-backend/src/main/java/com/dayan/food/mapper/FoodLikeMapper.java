package com.dayan.food.mapper;

import com.dayan.food.entity.po.FoodLike;
import org.apache.ibatis.annotations.Param;

public interface FoodLikeMapper {

    int insertIgnore(FoodLike foodLike);

    int deleteIgnore(@Param("foodId") Long foodId, @Param("userId") Long userId);

    int countByFoodId(@Param("foodId") Long foodId);

    int exists(@Param("foodId") Long foodId, @Param("userId") Long userId);
}