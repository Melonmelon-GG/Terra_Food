package com.dayan.food.entity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationCodeSendDTO(
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(max = 36)
        String captchaId,

        @NotBlank
        @Size(max = 10)
        String captchaAnswer
) {
}