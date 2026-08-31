package com.dayan.food.entity.dto;

import com.dayan.food.entity.enums.ReviewField;
import com.dayan.food.entity.enums.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewItemDTO(
        @NotNull ReviewField field,
        @NotNull ReviewStatus status
) {
}