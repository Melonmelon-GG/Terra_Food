package com.dayan.food.entity.po;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class FoodComment {

    private Long id;

    private Long foodId;

    private AppUser author;

    private String content;

    private LocalDateTime createdAt;

    public FoodComment(Long foodId, AppUser author, String content) {
        this.foodId = foodId;
        this.author = author;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }
}
