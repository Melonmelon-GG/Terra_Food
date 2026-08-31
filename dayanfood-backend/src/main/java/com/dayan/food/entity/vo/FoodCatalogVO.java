package com.dayan.food.entity.vo;

import java.io.Serializable;
import java.util.List;

/**
 * 目录分页视图：与后台管理用的 FoodPageVO 区分，只承载目录所需的
 * 条目、总数与页码，避免每次目录请求附带管理侧的全站热度统计查询。
 */
public record FoodCatalogVO(
        List<FoodVO> items,
        int total,
        int page,
        int pageSize
) implements Serializable {
}