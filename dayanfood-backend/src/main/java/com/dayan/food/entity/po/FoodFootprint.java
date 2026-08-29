package com.dayan.food.entity.po;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class FoodFootprint {

    private Food food;

    private LocalDateTime visitedAt;
}
