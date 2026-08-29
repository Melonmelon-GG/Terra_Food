package com.dayan.food.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AgentChatDTO(
        @NotBlank @Size(max = 1000) String message,
        @Size(max = 100) List<@Size(max = 200) String> availableTracks,
        @Positive Long currentFoodId
) {
}
