package com.dayan.food.entity.vo;

import com.dayan.food.entity.po.FoodFootprint;

import java.time.LocalDateTime;

public record FoodFootprintVO(FoodVO food, LocalDateTime visitedAt) {

    public static FoodFootprintVO from(FoodFootprint footprint) {
        return new FoodFootprintVO(FoodVO.from(footprint.getFood()), footprint.getVisitedAt());
    }
}
