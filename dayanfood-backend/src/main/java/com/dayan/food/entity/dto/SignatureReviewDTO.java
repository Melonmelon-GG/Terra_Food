package com.dayan.food.entity.dto;

import com.dayan.food.entity.enums.SignatureStatus;
import jakarta.validation.constraints.NotNull;

public record SignatureReviewDTO(
        @NotNull SignatureStatus status
) {
}