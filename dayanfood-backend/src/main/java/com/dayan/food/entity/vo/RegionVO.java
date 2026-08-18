package com.dayan.food.entity.vo;

import com.dayan.food.entity.po.Region;

public record RegionVO(
        Long id,
        String name,
        String province,
        String description
) {

    public static RegionVO from(Region region) {
        return new RegionVO(
                region.getId(),
                region.getName(),
                region.getProvince(),
                region.getDescription()
        );
    }
}
