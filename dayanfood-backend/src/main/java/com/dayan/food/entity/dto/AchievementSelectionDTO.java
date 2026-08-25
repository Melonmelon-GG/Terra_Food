package com.dayan.food.entity.dto;

import jakarta.validation.constraints.NotNull;

public record AchievementSelectionDTO(
        @NotNull
        Long achievementId
) {
}
