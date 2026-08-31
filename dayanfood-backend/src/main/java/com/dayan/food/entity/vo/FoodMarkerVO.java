package com.dayan.food.entity.vo;

import com.dayan.food.entity.po.FoodMarker;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 地图标记视图：面向 Leaflet 弹窗的最小契约（名称/地区/坐标/摘要）。
 */
public record FoodMarkerVO(
        Long id,
        String name,
        RegionVO region,
        BigDecimal latitude,
        BigDecimal longitude,
        String summary
) implements Serializable {

    public static FoodMarkerVO from(FoodMarker marker) {
        return new FoodMarkerVO(
                marker.getId(),
                marker.getName(),
                RegionVO.from(marker.getRegion()),
                marker.getLatitude(),
                marker.getLongitude(),
                marker.getSummary()
        );
    }
}