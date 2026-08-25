package com.dayan.food.entity.vo;

import com.dayan.food.entity.po.Achievement;

import java.time.LocalDateTime;

public record AchievementVO(
        Long id,
        String code,
        String name,
        String description,
        String imageUrl,
        LocalDateTime unlockedAt,
        boolean selected
) {

    public static AchievementVO from(Achievement achievement) {
        return new AchievementVO(
                achievement.getId(),
                achievement.getCode(),
                achievement.getName(),
                achievement.getDescription(),
                achievement.getImageUrl(),
                achievement.getUnlockedAt(),
                achievement.isSelected()
        );
    }
}
