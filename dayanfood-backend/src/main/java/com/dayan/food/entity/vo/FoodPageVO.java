package com.dayan.food.entity.vo;

import java.util.List;

public record FoodPageVO(
        List<FoodVO> items,
        int total,
        int page,
        int pageSize,
        long totalHeat,
        int pendingTotal
) {
}
