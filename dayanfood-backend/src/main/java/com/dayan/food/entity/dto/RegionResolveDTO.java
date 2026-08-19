package com.dayan.food.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegionResolveDTO(
        @NotBlank @Size(max = 50) String province,
        @NotBlank @Size(max = 50) String city
) {
}
