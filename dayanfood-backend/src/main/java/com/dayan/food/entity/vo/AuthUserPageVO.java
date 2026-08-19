package com.dayan.food.entity.vo;

import java.util.List;

public record AuthUserPageVO(
        List<AuthUserVO> items,
        int total,
        int page,
        int pageSize
) {
}
