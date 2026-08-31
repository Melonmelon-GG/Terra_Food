package com.dayan.food.entity.vo;

import com.dayan.food.entity.po.EtchingDesign;

import java.time.LocalDateTime;
import java.util.List;

public record EtchingDesignVO(
        Long id,
        String name,
        List<String> layerOne,
        boolean selected,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
