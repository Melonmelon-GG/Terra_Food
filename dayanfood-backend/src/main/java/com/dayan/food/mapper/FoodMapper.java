package com.dayan.food.mapper;

import com.dayan.food.entity.po.Food;
import com.dayan.food.entity.enums.FoodReviewStatus;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FoodMapper {

    List<Food> findList(
            @Param("keyword") String keyword,
            @Param("regionId") Long regionId,
            @Param("minLatitude") java.math.BigDecimal minLatitude,
            @Param("maxLatitude") java.math.BigDecimal maxLatitude,
            @Param("minLongitude") java.math.BigDecimal minLongitude,
            @Param("maxLongitude") java.math.BigDecimal maxLongitude,
            @Param("limit") int limit
    );

    List<Food> findAdminPage(
            @Param("status") FoodReviewStatus status,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    int countAdmin(@Param("status") FoodReviewStatus status);

    long sumHeat();

    int countPending();

    int countByImageUrl(String imageUrl);

    Food findById(Long id);

    int insert(Food food);

    int countDuplicate(
            @Param("name") String name,
            @Param("regionId") Long regionId,
            @Param("address") String address
    );

    int insertDailyVisit(@Param("foodId") Long foodId, @Param("username") String username);

    int incrementHeat(Long id);

    int updateReviewStatus(
            @Param("id") Long id,
            @Param("status") FoodReviewStatus status,
            @Param("reviewedBy") String reviewedBy
    );

    int deleteById(Long id);
}
