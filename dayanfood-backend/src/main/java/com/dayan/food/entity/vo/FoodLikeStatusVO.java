package com.dayan.food.entity.vo;

import java.io.Serializable;

/**
 * 菜品点赞状态的面向前端视图：总数 + 当前登录用户是否已赞。
 */
public record FoodLikeStatusVO(
        long likeCount,
        boolean likedByMe
) implements Serializable {
}