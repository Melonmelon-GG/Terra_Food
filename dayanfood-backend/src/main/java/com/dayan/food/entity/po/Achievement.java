package com.dayan.food.entity.po;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class Achievement {

    private Long id;

    private String code;

    private String name;

    private String description;

    private String imageUrl;

    private LocalDateTime unlockedAt;

    private LocalDateTime notifiedAt;

    private boolean selected;
}
