package com.dayan.food.entity.dto;

import com.dayan.food.entity.enums.UserRole;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateDTO(
        @NotNull UserRole role
) {
}
