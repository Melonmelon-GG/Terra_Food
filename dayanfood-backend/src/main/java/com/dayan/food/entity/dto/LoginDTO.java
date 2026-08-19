package com.dayan.food.entity.dto;

import com.dayan.food.entity.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginDTO(
        @NotBlank String username,
        @NotBlank String password,
        @NotNull UserRole role
) {
}
