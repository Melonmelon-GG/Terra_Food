package com.dayan.food.entity.vo;

import com.dayan.food.entity.po.Food;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FoodVO(
        Long id,
        String name,
        RegionVO region,
        BigDecimal latitude,
        BigDecimal longitude,
        String summary,
        String story,
        String ingredients,
        String imageUrl,
        Integer heat,
        LocalDateTime createdAt
) {

    public static FoodVO from(Food food) {
        return new FoodVO(
                food.getId(),
                food.getName(),
                RegionVO.from(food.getRegion()),
                food.getLatitude(),
                food.getLongitude(),
                food.getSummary(),
                food.getStory(),
                food.getIngredients(),
                food.getImageUrl(),
                food.getHeat(),
                food.getCreatedAt()
        );
    }
}
