package com.dayan.food.service;

import com.dayan.food.entity.dto.FoodUpdateDTO;
import com.dayan.food.entity.dto.FoodCreateDTO;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.entity.vo.FoodCatalogVO;
import com.dayan.food.entity.vo.FoodFootprintVO;
import com.dayan.food.entity.vo.FoodMarkerVO;
import com.dayan.food.entity.vo.FoodPageVO;
import com.dayan.food.entity.vo.FoodMapResultsVO;
import com.dayan.food.entity.vo.FoodMapClustersVO;
import com.dayan.food.entity.enums.FoodReviewStatus;

import java.math.BigDecimal;
import java.util.List;

public interface FoodService {
    FoodCatalogVO listMinePage(String username, int page, int pageSize);
    FoodVO ownedDetail(Long id, String username);
    FoodCatalogVO mapClusterMembers(String clusterId,String keyword,Long regionId,List<Long> tasteIds,
        List<Long> ingredientIds,List<Long> cuisineIds,java.math.BigDecimal minLatitude,
        java.math.BigDecimal maxLatitude,java.math.BigDecimal minLongitude,java.math.BigDecimal maxLongitude,int page,int pageSize);


    FoodVO create(FoodCreateDTO request, String username);

    List<FoodVO> list(
            String keyword,
            Long regionId,
            BigDecimal minLatitude,
            BigDecimal maxLatitude,
            BigDecimal minLongitude,
            BigDecimal maxLongitude
    );

    List<FoodMarkerVO> markers(
            String keyword,
            Long regionId,
            BigDecimal minLatitude,
            BigDecimal maxLatitude,
            BigDecimal minLongitude,
            BigDecimal maxLongitude
    );

    FoodCatalogVO catalog(String keyword, Long regionId, int page, int pageSize);

    FoodCatalogVO filteredCatalog(String keyword, Long regionId, List<Long> tasteIds,
            List<Long> ingredientIds, List<Long> cuisineIds, String sort,
            BigDecimal minLatitude, BigDecimal maxLatitude, BigDecimal minLongitude,
            BigDecimal maxLongitude, int page, int pageSize, boolean compact);

    FoodMapResultsVO filteredMap(String keyword, Long regionId, List<Long> tasteIds,
            List<Long> ingredientIds, List<Long> cuisineIds, String sort,
            BigDecimal minLatitude, BigDecimal maxLatitude, BigDecimal minLongitude,
            BigDecimal maxLongitude);

    FoodMapClustersVO mapClusters(String keyword, Long regionId, List<Long> tasteIds,
            List<Long> ingredientIds, List<Long> cuisineIds, BigDecimal minLatitude,
            BigDecimal maxLatitude, BigDecimal minLongitude, BigDecimal maxLongitude, int zoom);

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

    void review(Long id, FoodReviewStatus status, long expectedVersion, String reviewedBy);

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
