package com.dayan.food.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisplayNameUpdateDTO(
        @NotBlank
        @Size(max = 50)
        String displayName
) {
}