package com.dayan.food.entity.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "food")
@Getter
@NoArgsConstructor
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "region_id")
    private Region region;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String story;

    @Column(nullable = false, length = 500)
    private String ingredients;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private Integer heat;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Food(
            String name,
            Region region,
            BigDecimal latitude,
            BigDecimal longitude,
            String summary,
            String story,
            String ingredients,
            String imageUrl
    ) {
        this.name = name;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
        this.summary = summary;
        this.story = story;
        this.ingredients = ingredients;
        this.imageUrl = imageUrl;
        this.heat = 0;
        this.createdAt = LocalDateTime.now();
    }
}
