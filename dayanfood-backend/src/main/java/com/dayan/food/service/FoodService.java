package com.dayan.food.service;

import com.dayan.food.entity.dto.FoodUpdateDTO;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.entity.vo.FoodFootprintVO;
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

    List<FoodVO> listMine(String username);

    List<FoodFootprintVO> listRecentVisits(String username, int limit);

    List<FoodVO> recommend(
            String username,
            String province,
            String city,
            boolean personalized,
            int limit
    );

    FoodVO updateMine(Long id, FoodUpdateDTO request, String username);

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
            String remark,
            String createdBy
    );

    void delete(Long id);
}
