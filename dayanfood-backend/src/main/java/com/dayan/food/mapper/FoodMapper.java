package com.dayan.food.mapper;

import com.dayan.food.entity.po.Food;
import com.dayan.food.entity.po.FoodMarker;
import com.dayan.food.entity.po.FoodFootprint;
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

    List<FoodMarker> findMarkers(
            @Param("keyword") String keyword,
            @Param("regionId") Long regionId,
            @Param("minLatitude") java.math.BigDecimal minLatitude,
            @Param("maxLatitude") java.math.BigDecimal maxLatitude,
            @Param("minLongitude") java.math.BigDecimal minLongitude,
            @Param("maxLongitude") java.math.BigDecimal maxLongitude,
            @Param("limit") int limit
    );

    List<Food> findCatalogPage(
            @Param("keyword") String keyword,
            @Param("regionId") Long regionId,
            @Param("minLatitude") java.math.BigDecimal minLatitude,
            @Param("maxLatitude") java.math.BigDecimal maxLatitude,
            @Param("minLongitude") java.math.BigDecimal minLongitude,
            @Param("maxLongitude") java.math.BigDecimal maxLongitude,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    int countCatalog(
            @Param("keyword") String keyword,
            @Param("regionId") Long regionId,
            @Param("minLatitude") java.math.BigDecimal minLatitude,
            @Param("maxLatitude") java.math.BigDecimal maxLatitude,
            @Param("minLongitude") java.math.BigDecimal minLongitude,
            @Param("maxLongitude") java.math.BigDecimal maxLongitude
    );

    List<Food> findByCreatedBy(String username);

    List<Food> findApprovedByCreatedBy(@Param("username") String username, @Param("limit") int limit);

    Food findOwnedById(@Param("id") Long id, @Param("username") String username);

    int updateOwnedDetails(
            @Param("id") Long id,
            @Param("username") String username,
            @Param("name") String name,
            @Param("regionId") Long regionId,
            @Param("latitude") java.math.BigDecimal latitude,
            @Param("longitude") java.math.BigDecimal longitude,
            @Param("address") String address,
            @Param("summary") String summary,
            @Param("story") String story,
            @Param("ingredients") String ingredients,
            @Param("imageUrl") String imageUrl,
            @Param("remark") String remark,
            @Param("status") FoodReviewStatus status,
            @Param("reviewedBy") String reviewedBy
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

    int touchDailyVisit(@Param("foodId") Long foodId, @Param("username") String username);

    List<FoodFootprint> findRecentVisits(@Param("username") String username, @Param("limit") int limit);

    List<Food> findAgentRecommendations(
            @Param("username") String username,
            @Param("province") String province,
            @Param("city") String city,
            @Param("personalized") boolean personalized,
            @Param("limit") int limit
    );

    int incrementHeat(Long id);

    int updateReviewStatus(
            @Param("id") Long id,
            @Param("status") FoodReviewStatus status,
            @Param("reviewedBy") String reviewedBy
    );

    int deleteById(Long id);
}
