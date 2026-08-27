package com.dayan.food.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignatureUpdateDTO(
        @NotBlank
        @Size(max = 200)
        String signature
) {
}