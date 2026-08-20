package com.dayan.food.service.impl;

import com.dayan.food.entity.po.Food;
import com.dayan.food.entity.enums.FoodReviewStatus;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.entity.vo.FoodPageVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodMapper;
import com.dayan.food.mapper.RegionMapper;
import com.dayan.food.service.FoodService;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class FoodServiceImpl implements FoodService {

    private static final int MAP_RESULT_LIMIT = 500;

    private final FoodMapper foodMapper;
    private final RegionMapper regionMapper;
    private final AppUserMapper appUserMapper;
    private final CacheManager cacheManager;

    public FoodServiceImpl(
            FoodMapper foodMapper,
            RegionMapper regionMapper,
            AppUserMapper appUserMapper,
            CacheManager cacheManager
    ) {
        this.foodMapper = foodMapper;
        this.regionMapper = regionMapper;
        this.appUserMapper = appUserMapper;
        this.cacheManager = cacheManager;
    }

    @Override
    @Cacheable(
            cacheNames = "foodLists",
            key = "(#keyword == null ? '' : #keyword.trim()) + ':' + (#regionId == null ? '' : #regionId)",
            condition = "#minLatitude == null && #maxLatitude == null && #minLongitude == null && #maxLongitude == null"
    )
    @Transactional(readOnly = true)
    public List<FoodVO> list(
            String keyword,
            Long regionId,
            BigDecimal minLatitude,
            BigDecimal maxLatitude,
            BigDecimal minLongitude,
            BigDecimal maxLongitude
    ) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.length() > 100) {
            throw new IllegalArgumentException("搜索关键词不能超过 100 个字符");
        }
        validateBounds(minLatitude, maxLatitude, minLongitude, maxLongitude);

        // XML 中的 choose 保持原有规则：同时传入条件时优先按关键词检索。
        return foodMapper.findList(
                        normalizedKeyword,
                        regionId,
                        minLatitude,
                        maxLatitude,
                        minLongitude,
                        maxLongitude,
                        MAP_RESULT_LIMIT
                ).stream()
                .map(FoodVO::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FoodPageVO listForAdmin(int page, int pageSize, FoodReviewStatus status) {
        int normalizedPageSize = normalizePageSize(pageSize);
        int total = foodMapper.countAdmin(status);
        int totalPages = Math.max(1, (int) Math.ceil((double) total / normalizedPageSize));
        int normalizedPage = Math.min(Math.max(1, page), totalPages);
        int offset = (normalizedPage - 1) * normalizedPageSize;
        var items = foodMapper.findAdminPage(status, offset, normalizedPageSize).stream()
                .map(FoodVO::from)
                .toList();
        return new FoodPageVO(
                items,
                total,
                normalizedPage,
                normalizedPageSize,
                foodMapper.sumHeat(),
                foodMapper.countPending()
        );
    }

    @Override
    @Cacheable(cacheNames = "foodDetails", key = "#id")
    @Transactional(readOnly = true)
    public FoodVO detail(Long id) {
        Food food = foodMapper.findById(id);
        if (food == null) {
            throw notFound("美食不存在");
        }
        return FoodVO.from(food);
    }

    @Override
    @Transactional
    public void recordVisit(Long id, String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        if (foodMapper.insertDailyVisit(id, username) == 0) {
            return;
        }
        if (foodMapper.incrementHeat(id) != 1) {
            throw notFound("美食不存在");
        }

        var detailCache = cacheManager.getCache("foodDetails");
        if (detailCache != null) {
            detailCache.evict(id);
        }
        var listCache = cacheManager.getCache("foodLists");
        if (listCache != null) {
            listCache.clear();
        }
    }

    @Override
    @Transactional
    public void review(Long id, FoodReviewStatus status, String reviewedBy) {
        if (status != FoodReviewStatus.APPROVED && status != FoodReviewStatus.REJECTED) {
            throw new IllegalArgumentException("审批结果只能是通过或驳回");
        }
        if (foodMapper.updateReviewStatus(id, status, reviewedBy) != 1) {
            throw new IllegalArgumentException("待审批菜品不存在或已经处理");
        }
        clearFoodCaches(id);
    }

    @Override
    @CacheEvict(cacheNames = "foodLists", allEntries = true)
    @Transactional
    public FoodVO create(
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
    ) {
        var uploader = appUserMapper.findByUsername(createdBy);
        if (uploader == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在");
        }
        if (appUserMapper.claimFoodUpload(createdBy) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "每名用户三天内只能上传一次菜品"
            );
        }

        var region = regionMapper.findById(regionId);
        if (region == null) {
            throw notFound("地区不存在");
        }

        var food = new Food(
                name,
                region,
                latitude,
                longitude,
                address,
                summary,
                story,
                ingredients,
                imageUrl,
                createdBy,
                uploader.getRole() == UserRole.ADMIN
                        ? FoodReviewStatus.APPROVED
                        : FoodReviewStatus.PENDING
        );
        foodMapper.insert(food);
        return FoodVO.from(food);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "foodLists", allEntries = true),
            @CacheEvict(cacheNames = "foodDetails", key = "#id")
    })
    @Transactional
    public void delete(Long id) {
        if (foodMapper.deleteById(id) == 0) {
            throw notFound("美食不存在");
        }
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private int normalizePageSize(int pageSize) {
        return switch (pageSize) {
            case 20, 50 -> pageSize;
            default -> 10;
        };
    }

    private void validateBounds(
            BigDecimal minLatitude,
            BigDecimal maxLatitude,
            BigDecimal minLongitude,
            BigDecimal maxLongitude
    ) {
        boolean anyBound = minLatitude != null || maxLatitude != null
                || minLongitude != null || maxLongitude != null;
        boolean allBounds = minLatitude != null && maxLatitude != null
                && minLongitude != null && maxLongitude != null;
        if (anyBound && !allBounds) {
            throw new IllegalArgumentException("地图范围参数必须完整");
        }
        if (!allBounds) {
            return;
        }
        if (minLatitude.compareTo(maxLatitude) > 0 || minLongitude.compareTo(maxLongitude) > 0
                || minLatitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || maxLatitude.compareTo(BigDecimal.valueOf(90)) > 0
                || minLongitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || maxLongitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("地图范围参数无效");
        }
    }

    private void clearFoodCaches(Long id) {
        var detailCache = cacheManager.getCache("foodDetails");
        if (detailCache != null) {
            detailCache.evict(id);
        }
        var listCache = cacheManager.getCache("foodLists");
        if (listCache != null) {
            listCache.clear();
        }
    }
}
