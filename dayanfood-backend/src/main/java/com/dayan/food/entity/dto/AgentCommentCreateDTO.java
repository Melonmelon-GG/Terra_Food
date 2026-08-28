package com.dayan.food.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentCommentCreateDTO(
        @NotBlank String username,
        @NotNull Long foodId,
        @NotBlank @Size(max = 500) String content
) {
}
