package com.dayan.food.service;

import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.entity.vo.FoodPageVO;
import com.dayan.food.entity.enums.FoodReviewStatus;

import java.math.BigDecimal;
import java.util.List;

public interface FoodService {

    List<FoodVO> list(
            String keyword,
            Long regionId,
            BigDecimal minLatitude,
            BigDecimal maxLatitude,
            BigDecimal minLongitude,
            BigDecimal maxLongitude
    );

    FoodPageVO listForAdmin(int page, int pageSize, FoodReviewStatus status);

    FoodVO detail(Long id);

    void recordVisit(Long id, String username);

    void review(Long id, FoodReviewStatus status, String reviewedBy);

    FoodVO create(
            String name,
            Long regionId,
            BigDecimal latitude,
            BigDecimal longitude,
            String address,
            String summary,
            String story,
            String ingredients,
            String imageUrl,
            String createdBy
    );

    void delete(Long id);
}
