package com.dayan.food.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AvatarUpdateDTO(
        @NotBlank
        @Size(max = 500)
        @Pattern(regexp = "^(?:https?://.*|/uploads/.*)$", message = "头像地址必须是 HTTP(S) 地址或站内上传地址")
        String avatarUrl
) {
}
