package com.dayan.food.entity.vo;

import java.math.BigDecimal;

public record CityCenterVO(
        String city,
        String province,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
