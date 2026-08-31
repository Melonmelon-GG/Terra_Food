package com.dayan.food.entity.po;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 地图标记的轻量持久化映射：只取弹窗所需的字段，不携带 story/ingredients 等大字段，
 * 让地图拖动刷新（高频、不缓存）的载荷最小化。
 */
@Getter
@NoArgsConstructor
public class FoodMarker {

    private Long id;

    private String name;

    private Region region;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String summary;
}