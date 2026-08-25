package com.dayan.food.entity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetDTO(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9_]+$")
        String username,

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Pattern(regexp = "^\\d{6}$")
        String verificationCode,

        @NotBlank
        @Size(min = 8, max = 16, message = "密码必须是8到16位字母和数字的组合")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]+$",
                message = "密码必须同时包含字母和数字且仅由字母与数字组成"
        )
        String newPassword
) {
}
