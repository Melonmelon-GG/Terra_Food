package com.dayan.food.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterDTO(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9_]+$")
        String username,

        @NotBlank
        @Size(min = 6, max = 72)
        String password,

        @NotBlank
        @Size(min = 2, max = 50)
        String displayName
) {
}
