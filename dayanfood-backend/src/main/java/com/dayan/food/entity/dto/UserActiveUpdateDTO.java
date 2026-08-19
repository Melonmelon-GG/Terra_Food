package com.dayan.food.entity.dto;

import jakarta.validation.constraints.NotNull;

public record UserActiveUpdateDTO(
        @NotNull
        Boolean active
) {
}
