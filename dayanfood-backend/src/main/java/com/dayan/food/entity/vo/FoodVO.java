package com.dayan.food.entity.vo;

import com.dayan.food.entity.po.Food;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 面向前端的菜品视图，隔离 MyBatis 持久化对象与公开 API 契约。
 */
public record FoodVO(
        Long id,
        String name,
        RegionVO region,
        BigDecimal latitude,
        BigDecimal longitude,
        String address,
        String summary,
        String story,
        String ingredients,
        String imageUrl,
        Integer heat,
        String createdBy,
        LocalDateTime createdAt
) implements Serializable {

    public static FoodVO from(Food food) {
        return new FoodVO(
                food.getId(),
                food.getName(),
                RegionVO.from(food.getRegion()),
                food.getLatitude(),
                food.getLongitude(),
                food.getAddress(),
                food.getSummary(),
                food.getStory(),
                food.getIngredients(),
                food.getImageUrl(),
                food.getHeat(),
                food.getCreatedBy(),
                food.getCreatedAt()
        );
    }
}
