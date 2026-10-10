package com.dayan.food.mapper;

import com.dayan.food.entity.po.Food;
import com.dayan.food.entity.po.FoodMarker;
import com.dayan.food.entity.po.FoodFootprint;
import com.dayan.food.entity.po.FoodMapClusterRow;
import com.dayan.food.entity.enums.FoodReviewStatus;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FoodMapper {
    List<Food> findMinePage(@Param("userId") Long userId,@Param("offset") int offset,@Param("limit") int limit);
    int countMine(@Param("userId") Long userId);
    List<Food> findClusterMembers(java.util.Map<String,Object> query);
    int countClusterMembers(java.util.Map<String,Object> query);
    List<Food> findApprovedByIds(@Param("ids") List<Long> ids);


    int updateLocationLabels(@Param("id") Long id,
            @Param("province") String province, @Param("city") String city);

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

    List<FoodMarker> findFilteredMarkers(
            @Param("keyword") String keyword,
            @Param("tokens") List<String> tokens,
            @Param("regionId") Long regionId,
            @Param("tasteIds") List<Long> tasteIds,
            @Param("ingredientIds") List<Long> ingredientIds,
            @Param("cuisineIds") List<Long> cuisineIds,
            @Param("minLatitude") java.math.BigDecimal minLatitude,
            @Param("maxLatitude") java.math.BigDecimal maxLatitude,
            @Param("minLongitude") java.math.BigDecimal minLongitude,
            @Param("maxLongitude") java.math.BigDecimal maxLongitude,
            @Param("sort") String sort,
            @Param("limit") int limit
    );

    List<Food> findFilteredCatalogPage(
            @Param("keyword") String keyword,
            @Param("tokens") List<String> tokens,
            @Param("regionId") Long regionId,
            @Param("tasteIds") List<Long> tasteIds,
            @Param("ingredientIds") List<Long> ingredientIds,
            @Param("cuisineIds") List<Long> cuisineIds,
            @Param("minLatitude") java.math.BigDecimal minLatitude,
            @Param("maxLatitude") java.math.BigDecimal maxLatitude,
            @Param("minLongitude") java.math.BigDecimal minLongitude,
            @Param("maxLongitude") java.math.BigDecimal maxLongitude,
            @Param("sort") String sort,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    List<Food> findFilteredCatalogCards(
            @Param("keyword") String keyword, @Param("tokens") List<String> tokens,
            @Param("regionId") Long regionId, @Param("tasteIds") List<Long> tasteIds,
            @Param("ingredientIds") List<Long> ingredientIds, @Param("cuisineIds") List<Long> cuisineIds,
            @Param("minLatitude") java.math.BigDecimal minLatitude, @Param("maxLatitude") java.math.BigDecimal maxLatitude,
            @Param("minLongitude") java.math.BigDecimal minLongitude, @Param("maxLongitude") java.math.BigDecimal maxLongitude,
            @Param("sort") String sort, @Param("offset") int offset, @Param("pageSize") int pageSize);

    int countFilteredCatalog(
            @Param("tokens") List<String> tokens,
            @Param("regionId") Long regionId,
            @Param("tasteIds") List<Long> tasteIds,
            @Param("ingredientIds") List<Long> ingredientIds,
            @Param("cuisineIds") List<Long> cuisineIds,
            @Param("minLatitude") java.math.BigDecimal minLatitude,
            @Param("maxLatitude") java.math.BigDecimal maxLatitude,
            @Param("minLongitude") java.math.BigDecimal minLongitude,
            @Param("maxLongitude") java.math.BigDecimal maxLongitude
    );

    List<FoodMapClusterRow> findMapClusters(
            @Param("tokens") List<String> tokens, @Param("regionId") Long regionId,
            @Param("tasteIds") List<Long> tasteIds, @Param("ingredientIds") List<Long> ingredientIds,
            @Param("cuisineIds") List<Long> cuisineIds,
            @Param("minLatitude") java.math.BigDecimal minLatitude, @Param("maxLatitude") java.math.BigDecimal maxLatitude,
            @Param("minLongitude") java.math.BigDecimal minLongitude, @Param("maxLongitude") java.math.BigDecimal maxLongitude,
            @Param("zoom") int zoom, @Param("limit") int limit);

    List<Food> findByCreatedBy(@Param("userId") Long userId);

    List<Food> findApprovedByCreatedBy(@Param("userId") Long userId, @Param("limit") int limit);

    List<Food> findMatchingCandidates(@Param("tokens") List<String> tokens, @Param("limit") int limit);

    Food findOwnedById(@Param("id") Long id, @Param("userId") Long userId);

    int updateOwnedDetails(
            @Param("id") Long id,
            @Param("userId") Long userId,
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

    int insertDailyVisit(@Param("foodId") Long foodId, @Param("userId") Long userId);

    int touchDailyVisit(@Param("foodId") Long foodId, @Param("userId") Long userId);

    List<FoodFootprint> findRecentVisits(@Param("userId") Long userId, @Param("limit") int limit);

    List<Food> findAgentRecommendations(
            @Param("userId") Long userId,
            @Param("province") String province,
            @Param("city") String city,
            @Param("personalized") boolean personalized,
            @Param("limit") int limit
    );

    int incrementHeat(Long id);

    int updateReviewStatus(
            @Param("id") Long id,
            @Param("status") FoodReviewStatus status,
            @Param("reviewedBy") String reviewedBy,
            @Param("expectedVersion") long expectedVersion
    );

    int deleteById(Long id);
}
