package com.dayan.food.entity.po;

import com.dayan.food.entity.enums.ReviewField;
import com.dayan.food.entity.enums.ReviewStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class UserReviewItem {

    private Long id;

    private Long userId;

    private ReviewField field;

    private String currentValue;

    private String pendingValue;

    private ReviewStatus status;

    private LocalDateTime requestedAt;

    private String reviewedBy;

    private LocalDateTime reviewedAt;
}