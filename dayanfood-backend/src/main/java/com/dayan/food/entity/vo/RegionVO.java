package com.dayan.food.entity.vo;

import com.dayan.food.entity.po.Region;

import java.io.Serializable;

public record RegionVO(
        Long id,
        String name,
        String province,
        String description
) implements Serializable {

    public static RegionVO from(Region region) {
        return new RegionVO(
                region.getId(),
                region.getName(),
                region.getProvince(),
                region.getDescription()
        );
    }
}
