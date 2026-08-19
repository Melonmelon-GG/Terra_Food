package com.dayan.food.mapper;

import com.dayan.food.entity.po.Food;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FoodMapper {

    List<Food> findList(@Param("keyword") String keyword, @Param("regionId") Long regionId);

    Food findById(Long id);

    int insert(Food food);

    int countDuplicate(
            @Param("name") String name,
            @Param("regionId") Long regionId,
            @Param("address") String address
    );

    int insertDailyVisit(@Param("foodId") Long foodId, @Param("username") String username);

    int incrementHeat(Long id);

    int deleteById(Long id);
}
