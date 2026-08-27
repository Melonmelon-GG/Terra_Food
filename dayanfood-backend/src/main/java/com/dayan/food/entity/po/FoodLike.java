package com.dayan.food.entity.po;

import java.time.LocalDateTime;

/**
 * 菜品点赞记录。复合主键 (food_id, user_id) 天然保证一人一菜只赞一次。
 */
public class FoodLike {

    private final Long foodId;
    private final Long userId;
    private final LocalDateTime createdAt;

    public FoodLike(Long foodId, Long userId) {
        this.foodId = foodId;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getFoodId() {
        return foodId;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}