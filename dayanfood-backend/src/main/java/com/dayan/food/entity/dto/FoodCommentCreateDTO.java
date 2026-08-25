package com.dayan.food.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FoodCommentCreateDTO(
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 500, message = "评论内容不能超过500个字符")
        String content
) {
}
