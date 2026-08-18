package com.dayan.food.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FoodCreateDTO(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        Long regionId,

        @NotNull
        BigDecimal latitude,

        @NotNull
        BigDecimal longitude,

        @NotBlank
        @Size(max = 1000)
        String summary,

        @NotBlank
        String story,

        @NotBlank
        @Size(max = 500)
        String ingredients,

        @Size(max = 500)
        String imageUrl
) {
}
