package com.dayan.food.entity.vo;

import com.dayan.food.entity.po.Region;

import java.io.Serializable;
import java.math.BigDecimal;

public record RegionVO(
        Long id,
        String name,
        String province,
        String description,
        BigDecimal centerLatitude,
        BigDecimal centerLongitude
) implements Serializable {

    public static RegionVO from(Region region) {
        return from(region, null);
    }

    public static RegionVO from(Region region, CityCenterVO center) {
        return new RegionVO(
                region.getId(),
                region.getName(),
                region.getProvince(),
                region.getDescription(),
                center == null ? null : center.latitude(),
                center == null ? null : center.longitude()
        );
    }
}
