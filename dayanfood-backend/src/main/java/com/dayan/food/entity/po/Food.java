package com.dayan.food.entity.po;

import com.dayan.food.entity.enums.FoodReviewStatus;
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
    private String imageSmallUrl;
    private String imageMediumUrl;
    private String imageLargeUrl;

    private String remark;

    private Integer heat;

    private FoodReviewStatus reviewStatus;

    private Long contentVersion;

    private String reviewedBy;

    private LocalDateTime reviewedAt;

    private String createdBy;

    private Long createdByUserId;

    private String ownershipStatus;

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
            String remark,
            String createdBy,
            FoodReviewStatus reviewStatus
    ) {
        this(name, region, latitude, longitude, address, summary, story, ingredients, imageUrl,
                remark, createdBy, null, reviewStatus);
    }

    public Food(
            String name, Region region, BigDecimal latitude, BigDecimal longitude, String address,
            String summary, String story, String ingredients, String imageUrl, String remark,
            String createdBy, Long createdByUserId, FoodReviewStatus reviewStatus
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
        this.remark = remark;
        this.heat = 0;
        this.reviewStatus = reviewStatus;
        if (reviewStatus == FoodReviewStatus.APPROVED) {
            this.reviewedBy = createdBy;
            this.reviewedAt = LocalDateTime.now();
        }
        this.createdBy = createdBy;
        this.createdByUserId = createdByUserId;
        this.ownershipStatus = "VERIFIED";
        this.createdAt = LocalDateTime.now();
    }
}
