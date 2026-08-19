package com.dayan.food.entity.po;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class Food {

    private Long id;

    private String name;

    private Region region;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String address;

    private String summary;

    private String story;

    private String ingredients;

    private String imageUrl;

    private Integer heat;

    private String createdBy;

    private LocalDateTime createdAt;

    public Food(
            String name,
            Region region,
            BigDecimal latitude,
            BigDecimal longitude,
            String address,
            String summary,
            String story,
            String ingredients,
            String imageUrl,
            String createdBy
    ) {
        this.name = name;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.summary = summary;
        this.story = story;
        this.ingredients = ingredients;
        this.imageUrl = imageUrl;
        this.heat = 0;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }
}
