package com.dayan.food.service.impl;

import com.dayan.food.entity.dto.FoodUpdateDTO;
import com.dayan.food.entity.po.Food;
import com.dayan.food.entity.enums.FoodReviewStatus;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.entity.vo.FoodFootprintVO;
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
    @Transactional(readOnly = true)
    public List<FoodVO> listMine(String username) {
        return foodMapper.findByCreatedBy(username).stream()
                .map(FoodVO::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodFootprintVO> listRecentVisits(String username, int limit) {
        int normalizedLimit = Math.min(Math.max(limit, 1), 50);
        return foodMapper.findRecentVisits(username, normalizedLimit).stream()
                .map(FoodFootprintVO::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodVO> recommend(
            String username,
            String province,
            String city,
            boolean personalized,
            int limit
    ) {
        int normalizedLimit = Math.min(Math.max(limit, 1), 10);
        return foodMapper.findAgentRecommendations(
                        username,
                        normalizeOptional(province),
                        normalizeOptional(city),
                        personalized,
                        normalizedLimit
                ).stream()
                .map(FoodVO::from)
                .toList();
    }

    @Override
    @Transactional
    public FoodVO updateMine(Long id, FoodUpdateDTO request, String username) {
        var owner = appUserMapper.findByUsername(username);
        if (owner == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在");
        }
        if (foodMapper.findOwnedById(id, username) == null) {
            throw notFound("只能补全自己上传的菜品");
        }
        if (regionMapper.findById(request.regionId()) == null) {
            throw notFound("地区不存在");
        }

        boolean isAdmin = owner.getRole() == UserRole.ADMIN || owner.getRole() == UserRole.SUB_ADMIN;
        FoodReviewStatus nextStatus = isAdmin ? FoodReviewStatus.APPROVED : FoodReviewStatus.PENDING;
        String reviewedBy = isAdmin ? username : null;
        int updated = foodMapper.updateOwnedDetails(
                id,
                username,
                request.name().trim(),
                request.regionId(),
                request.latitude(),
                request.longitude(),
                normalizeOptional(request.address()),
                request.summary().trim(),
                request.story().trim(),
                request.ingredients().trim(),
                normalizeOptional(request.imageUrl()),
                normalizeOptional(request.remark()),
                nextStatus,
                reviewedBy
        );
        if (updated != 1) {
            throw notFound("只能补全自己上传的菜品");
        }
        clearFoodCaches(id);
        return FoodVO.from(foodMapper.findOwnedById(id, username));
    }

    @Override
    @Cacheable(cacheNames = "foodDetails", key = "#id")
    @Transactional(readOnly = true)
    public FoodVO detail(Long id) {
        Food food = foodMapper.findById(id);
        if (food == null) {
            throw notFound("美食不存在");
        }
        return FoodVO.from(food, appUserMapper.findByUsername(food.getCreatedBy()));
    }

    @Override
    @Transactional
    public void recordVisit(Long id, String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        if (foodMapper.insertDailyVisit(id, username) == 0) {
            // 同一天再次浏览不重复增加热度，但要刷新足迹的最近访问时间。
            foodMapper.touchDailyVisit(id, username);
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
            String remark,
            String createdBy
    ) {
        var uploader = appUserMapper.findByUsername(createdBy);
        if (uploader == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在");
        }
        var region = regionMapper.findById(regionId);
        if (region == null) {
            throw notFound("地区不存在");
        }

        String normalizedImageUrl = imageUrl == null || imageUrl.isBlank() ? null : imageUrl.trim();

        var food = new Food(
                name,
                region,
                latitude,
                longitude,
                address,
                summary,
                story,
                ingredients,
                normalizedImageUrl,
                normalizeOptional(remark),
                createdBy,
                uploader.getRole() == UserRole.ADMIN || uploader.getRole() == UserRole.SUB_ADMIN
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

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
        // 经度允许 min > max：Leaflet 在世界视口跨过 180° 经线时 west/east 会表现为
        // minLongitude > maxLongitude（如 170 / -170），这表示跨经线查询而非参数错误；
        // 纬度始终要求 min <= max。
        if (minLatitude.compareTo(maxLatitude) > 0
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
