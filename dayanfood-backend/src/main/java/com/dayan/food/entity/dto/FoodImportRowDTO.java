package com.dayan.food.entity.dto;

import java.math.BigDecimal;

public record FoodImportRowDTO(
        int rowNumber,
        String province,
        String city,
        String name,
        String address,
        String summary,
        String story,
        String ingredients,
        BigDecimal latitude,
        BigDecimal longitude,
        String username
) {
}
