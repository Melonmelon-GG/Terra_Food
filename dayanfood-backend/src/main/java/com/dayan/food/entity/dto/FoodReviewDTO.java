package com.dayan.food.entity.dto;

import com.dayan.food.entity.enums.FoodReviewStatus;
import jakarta.validation.constraints.NotNull;

public record FoodReviewDTO(
        @NotNull FoodReviewStatus status
) {
}
