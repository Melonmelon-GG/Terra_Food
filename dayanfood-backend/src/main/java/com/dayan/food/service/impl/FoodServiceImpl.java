package com.dayan.food.service.impl;

import com.dayan.food.entity.dto.FoodCreateDTO;

import com.dayan.food.cache.CacheInvalidator;
import com.dayan.food.entity.dto.FoodUpdateDTO;
import com.dayan.food.entity.po.Food;
import com.dayan.food.entity.po.FoodMarker;
import com.dayan.food.entity.enums.FoodReviewStatus;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.entity.vo.FoodCatalogVO;
import com.dayan.food.entity.vo.FoodFootprintVO;
import com.dayan.food.entity.vo.FoodMarkerVO;
import com.dayan.food.entity.vo.FoodPageVO;
import com.dayan.food.entity.vo.FoodMapResultsVO;
import com.dayan.food.entity.vo.FoodMapClustersVO;
import com.dayan.food.entity.vo.FoodMapClusterItemVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodMapper;
import com.dayan.food.mapper.RegionMapper;
import com.dayan.food.service.FoodService;
import com.dayan.food.service.FoodTagService;
import com.dayan.food.service.DiscoveryCountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.text.Normalizer;

@Service
public class FoodServiceImpl implements FoodService {

    private static final int MAP_RESULT_LIMIT = 500;
    private static final int CATALOG_MAX_PAGE_SIZE = 500;
    private static final int MAP_CLUSTER_LIMIT = 300;
    @Autowired(required=false) private com.dayan.food.cache.DiscoveryVersion discoveryVersion;
    @Autowired(required=false) private com.dayan.food.cache.DiscoveryCache discoveryCache;

    private final FoodMapper foodMapper;
    private final RegionMapper regionMapper;
    private final AppUserMapper appUserMapper;
    private final CacheManager cacheManager;
    private final CacheInvalidator cacheInvalidator;
    private final FoodTagService foodTagService;
    private final DiscoveryCountService discoveryCountService;

    @Autowired
    public FoodServiceImpl(
            FoodMapper foodMapper,
            RegionMapper regionMapper,
            AppUserMapper appUserMapper,
            CacheManager cacheManager,
            CacheInvalidator cacheInvalidator,
            FoodTagService foodTagService,
            DiscoveryCountService discoveryCountService
    ) {
        this.foodMapper = foodMapper;
        this.regionMapper = regionMapper;
        this.appUserMapper = appUserMapper;
        this.cacheManager = cacheManager;
        this.cacheInvalidator = cacheInvalidator;
        this.foodTagService = foodTagService;
        this.discoveryCountService = discoveryCountService;
    }

    public FoodServiceImpl(FoodMapper foodMapper, RegionMapper regionMapper, AppUserMapper appUserMapper,
                           CacheManager cacheManager, CacheInvalidator cacheInvalidator) {
        this(foodMapper, regionMapper, appUserMapper, cacheManager, cacheInvalidator, null, null);
    }

    @Override
    public List<FoodVO> list(
            String keyword,
            Long regionId,
            BigDecimal minLatitude,
            BigDecimal maxLatitude,
            BigDecimal minLongitude,
            BigDecimal maxLongitude
    ) {
        if(discoveryCache==null)return listUncached(keyword, regionId, minLatitude, maxLatitude, minLongitude, maxLongitude);
        return discoveryCache.get("list:"+discoveryKey(new Object[]{keyword, regionId, minLatitude, maxLatitude, minLongitude, maxLongitude}),false,
                () -> listUncached(keyword, regionId, minLatitude, maxLatitude, minLongitude, maxLongitude));
    }

    private List<FoodVO> listUncached(
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
    public List<FoodMarkerVO> markers(
            String keyword,
            Long regionId,
            BigDecimal minLatitude,
            BigDecimal maxLatitude,
            BigDecimal minLongitude,
            BigDecimal maxLongitude
    ) {
        if(discoveryCache==null)return markersUncached(keyword, regionId, minLatitude, maxLatitude, minLongitude, maxLongitude);
        return discoveryCache.get("markers:"+discoveryKey(new Object[]{keyword, regionId, minLatitude, maxLatitude, minLongitude, maxLongitude}),keyword==null&&regionId==null&&minLatitude==null,
                () -> markersUncached(keyword, regionId, minLatitude, maxLatitude, minLongitude, maxLongitude));
    }

    private List<FoodMarkerVO> markersUncached(
            String keyword,
            Long regionId,
            BigDecimal minLatitude,
            BigDecimal maxLatitude,
            BigDecimal minLongitude,
            BigDecimal maxLongitude
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        validateBounds(minLatitude, maxLatitude, minLongitude, maxLongitude);

        // 地图高频拖动接口只回弹窗所需字段（名称/地区/坐标/摘要）。仅全国无筛选结果
        // 进入受限缓存；带视口或筛选条件的低命中请求仍直接查询数据库。
        return foodMapper.findMarkers(
                        normalizedKeyword,
                        regionId,
                        minLatitude,
                        maxLatitude,
                        minLongitude,
                        maxLongitude,
                        MAP_RESULT_LIMIT
                ).stream()
                .map(FoodMarkerVO::from)
                .toList();
    }

    @Override
    public FoodCatalogVO catalog(String keyword, Long regionId, int page, int pageSize) {
        if(discoveryCache==null)return catalogUncached(keyword, regionId, page, pageSize);
        return discoveryCache.get("catalog:"+discoveryKey(new Object[]{keyword, regionId, page, pageSize}),false,
                () -> catalogUncached(keyword, regionId, page, pageSize));
    }

    private FoodCatalogVO catalogUncached(String keyword, Long regionId, int page, int pageSize) {
        String normalizedKeyword = normalizeKeyword(keyword);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), CATALOG_MAX_PAGE_SIZE);
        int total = foodMapper.countCatalog(
                normalizedKeyword,
                regionId,
                null,
                null,
                null,
                null
        );
        int totalPages = Math.max(1, (int) Math.ceil((double) total / normalizedPageSize));
        int normalizedPage = Math.min(Math.max(page, 1), totalPages);
        int offset = (normalizedPage - 1) * normalizedPageSize;
        var items = foodMapper.findCatalogPage(
                        normalizedKeyword,
                        regionId,
                        null,
                        null,
                        null,
                        null,
                        offset,
                        normalizedPageSize
                ).stream()
                .map(FoodVO::from)
                .toList();
        return new FoodCatalogVO(items, total, normalizedPage, normalizedPageSize);
    }

    @Override
    public FoodCatalogVO filteredCatalog(String keyword, Long regionId, List<Long> tasteIds,
            List<Long> ingredientIds, List<Long> cuisineIds, String sort,
            BigDecimal minLatitude, BigDecimal maxLatitude, BigDecimal minLongitude,
            BigDecimal maxLongitude, int page, int pageSize, boolean compact) {
        if(discoveryCache==null)return filteredCatalogUncached(keyword, regionId, tasteIds, ingredientIds, cuisineIds, sort, minLatitude, maxLatitude, minLongitude, maxLongitude, page, pageSize, compact);
        return discoveryCache.get("filteredCatalog:"+discoveryKey(new Object[]{keyword, regionId, tasteIds, ingredientIds, cuisineIds, sort, minLatitude, maxLatitude, minLongitude, maxLongitude, page, pageSize, compact}),(keyword==null||keyword.isBlank())&&page>=1&&page<=3&&minLatitude==null,
                () -> filteredCatalogUncached(keyword, regionId, tasteIds, ingredientIds, cuisineIds, sort, minLatitude, maxLatitude, minLongitude, maxLongitude, page, pageSize, compact));
    }

    private FoodCatalogVO filteredCatalogUncached(String keyword, Long regionId, List<Long> tasteIds,
            List<Long> ingredientIds, List<Long> cuisineIds, String sort,
            BigDecimal minLatitude, BigDecimal maxLatitude, BigDecimal minLongitude,
            BigDecimal maxLongitude, int page, int pageSize, boolean compact) {
        SearchInput input = searchInput(keyword, tasteIds, ingredientIds, cuisineIds, sort,
                minLatitude, maxLatitude, minLongitude, maxLongitude);
        int size = Math.min(Math.max(pageSize, 1), 50);
        int total = discoveryCount(input.tokens(), regionId, input.tasteIds(), input.ingredientIds(),
                input.cuisineIds(), minLatitude, maxLatitude, minLongitude, maxLongitude);
        int pages = Math.max(1, (int) Math.ceil((double) total / size));
        int normalizedPage = Math.min(Math.max(page, 1), pages);
        var records = compact ? foodMapper.findFilteredCatalogCards(input.keyword(), input.tokens(),
                        regionId, input.tasteIds(), input.ingredientIds(), input.cuisineIds(),
                        minLatitude, maxLatitude, minLongitude, maxLongitude, input.sort(),
                        (normalizedPage - 1) * size, size)
                : foodMapper.findFilteredCatalogPage(input.keyword(), input.tokens(),
                        regionId, input.tasteIds(), input.ingredientIds(), input.cuisineIds(),
                        minLatitude, maxLatitude, minLongitude, maxLongitude, input.sort(),
                        (normalizedPage - 1) * size, size);
        List<FoodVO> items = records.stream()
                .map(FoodVO::from).toList();
        return new FoodCatalogVO(items, total, normalizedPage, size);
    }

    @Override
    public FoodMapResultsVO filteredMap(String keyword, Long regionId, List<Long> tasteIds,
            List<Long> ingredientIds, List<Long> cuisineIds, String sort,
            BigDecimal minLatitude, BigDecimal maxLatitude, BigDecimal minLongitude,
            BigDecimal maxLongitude) {
        if(discoveryCache==null)return filteredMapUncached(keyword, regionId, tasteIds, ingredientIds, cuisineIds, sort, minLatitude, maxLatitude, minLongitude, maxLongitude);
        return discoveryCache.get("filteredMap:"+discoveryKey(new Object[]{keyword, regionId, tasteIds, ingredientIds, cuisineIds, sort, minLatitude, maxLatitude, minLongitude, maxLongitude}),false,
                () -> filteredMapUncached(keyword, regionId, tasteIds, ingredientIds, cuisineIds, sort, minLatitude, maxLatitude, minLongitude, maxLongitude));
    }

    private FoodMapResultsVO filteredMapUncached(String keyword, Long regionId, List<Long> tasteIds,
            List<Long> ingredientIds, List<Long> cuisineIds, String sort,
            BigDecimal minLatitude, BigDecimal maxLatitude, BigDecimal minLongitude,
            BigDecimal maxLongitude) {
        SearchInput input = searchInput(keyword, tasteIds, ingredientIds, cuisineIds, sort,
                minLatitude, maxLatitude, minLongitude, maxLongitude);
        int total = foodMapper.countFilteredCatalog(input.tokens(), regionId, input.tasteIds(),
                input.ingredientIds(), input.cuisineIds(), minLatitude, maxLatitude,
                minLongitude, maxLongitude);
        List<FoodMarkerVO> items = foodMapper.findFilteredMarkers(input.keyword(), input.tokens(),
                        regionId, input.tasteIds(), input.ingredientIds(), input.cuisineIds(),
                        minLatitude, maxLatitude, minLongitude, maxLongitude, input.sort(),
                        MAP_RESULT_LIMIT).stream().map(FoodMarkerVO::from).toList();
        return new FoodMapResultsVO(items, total, total > items.size());
    }

    @Override
    public FoodMapClustersVO mapClusters(String keyword, Long regionId, List<Long> tasteIds,
            List<Long> ingredientIds, List<Long> cuisineIds, BigDecimal minLatitude,
            BigDecimal maxLatitude, BigDecimal minLongitude, BigDecimal maxLongitude, int zoom) {
        if(discoveryCache==null)return mapClustersUncached(keyword, regionId, tasteIds, ingredientIds, cuisineIds, minLatitude, maxLatitude, minLongitude, maxLongitude, zoom);
        return discoveryCache.get("mapClusters:"+discoveryKey(new Object[]{keyword, regionId, tasteIds, ingredientIds, cuisineIds, minLatitude, maxLatitude, minLongitude, maxLongitude, zoom}),false,
                () -> mapClustersUncached(keyword, regionId, tasteIds, ingredientIds, cuisineIds, minLatitude, maxLatitude, minLongitude, maxLongitude, zoom));
    }

    private FoodMapClustersVO mapClustersUncached(String keyword, Long regionId, List<Long> tasteIds,
            List<Long> ingredientIds, List<Long> cuisineIds, BigDecimal minLatitude,
            BigDecimal maxLatitude, BigDecimal minLongitude, BigDecimal maxLongitude, int zoom) {
        SearchInput input = searchInput(keyword, tasteIds, ingredientIds, cuisineIds, "RELEVANCE",
                minLatitude, maxLatitude, minLongitude, maxLongitude);
        int total = discoveryCount(input.tokens(), regionId, input.tasteIds(), input.ingredientIds(),
                input.cuisineIds(), minLatitude, maxLatitude, minLongitude, maxLongitude);
        int effectiveZoom = Math.min(Math.max(zoom, 1), 18);
        List<com.dayan.food.entity.po.FoodMapClusterRow> rows;
        do {
            rows = foodMapper.findMapClusters(input.tokens(), regionId, input.tasteIds(),
                    input.ingredientIds(), input.cuisineIds(), minLatitude, maxLatitude,
                    minLongitude, maxLongitude, effectiveZoom, MAP_CLUSTER_LIMIT + 1);
            if (rows.size() <= MAP_CLUSTER_LIMIT || effectiveZoom == 1) break;
            effectiveZoom--;
        } while (true);
        int resultZoom = effectiveZoom;
        var items = rows.stream().limit(MAP_CLUSTER_LIMIT)
                .map(row -> FoodMapClusterItemVO.from(row, resultZoom)).toList();
        return new FoodMapClustersVO(discoveryVersion == null ? 1 : discoveryVersion.current(), total, effectiveZoom, items);
    }

    @Override
    @Transactional(readOnly = true)
    public FoodPageVO listForAdmin(int page, int pageSize, FoodReviewStatus status) {
        requireAdministrator();
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

    @Override @Transactional(readOnly=true)
    public FoodCatalogVO listMinePage(String username,int page,int pageSize){
        var actor=com.dayan.food.security.AuthenticatedActor.resolve(appUserMapper,username);
        int size=Math.min(Math.max(pageSize,1),50); int total=foodMapper.countMine(actor.getId());
        int p=Math.min(Math.max(page,1),Math.max(1,(total+size-1)/size));
        return new FoodCatalogVO(foodMapper.findMinePage(actor.getId(),(p-1)*size,size).stream().map(FoodVO::from).toList(),total,p,size);
    }
    @Override @Transactional(readOnly=true)
    public FoodVO ownedDetail(Long id,String username){
        var actor=com.dayan.food.security.AuthenticatedActor.resolve(appUserMapper,username);
        var food=foodMapper.findOwnedById(id,actor.getId());
        if(food==null)throw notFound("原提交结果已不可访问");
        return FoodVO.from(food);
    }
    @Override
    public FoodCatalogVO mapClusterMembers(String clusterId,String keyword,Long regionId,List<Long> tasteIds,
            List<Long> ingredientIds,List<Long> cuisineIds,BigDecimal minLatitude,BigDecimal maxLatitude,
            BigDecimal minLongitude,BigDecimal maxLongitude,int page,int pageSize) {
        if(discoveryCache==null)return mapClusterMembersUncached(clusterId, keyword, regionId, tasteIds, ingredientIds, cuisineIds, minLatitude, maxLatitude, minLongitude, maxLongitude, page, pageSize);
        return discoveryCache.get("mapClusterMembers:"+discoveryKey(new Object[]{clusterId, keyword, regionId, tasteIds, ingredientIds, cuisineIds, minLatitude, maxLatitude, minLongitude, maxLongitude, page, pageSize}),false,
                () -> mapClusterMembersUncached(clusterId, keyword, regionId, tasteIds, ingredientIds, cuisineIds, minLatitude, maxLatitude, minLongitude, maxLongitude, page, pageSize));
    }

    private FoodCatalogVO mapClusterMembersUncached(String clusterId,String keyword,Long regionId,List<Long> tasteIds,
            List<Long> ingredientIds,List<Long> cuisineIds,BigDecimal minLatitude,BigDecimal maxLatitude,
            BigDecimal minLongitude,BigDecimal maxLongitude,int page,int pageSize) {
        if(clusterId==null||!clusterId.matches("\\d{1,2}:\\d{1,10}:\\d{1,10}"))throw new IllegalArgumentException("无效的地图聚合标识");
        String[] parts=clusterId.split(":"); int zoom=Integer.parseInt(parts[0]);long x=Long.parseLong(parts[1]),y=Long.parseLong(parts[2]);
        if(zoom<1||zoom>18||x<0||y<0||x>=(1L<<zoom)||y>=(1L<<zoom))throw new IllegalArgumentException("无效的地图聚合标识");
        var input=searchInput(keyword,tasteIds,ingredientIds,cuisineIds,"HEAT",minLatitude,maxLatitude,minLongitude,maxLongitude);
        var query=new java.util.HashMap<String,Object>();query.put("keyword",input.keyword());query.put("tokens",input.tokens());
        query.put("regionId",regionId);query.put("tasteIds",input.tasteIds());query.put("ingredientIds",input.ingredientIds());query.put("cuisineIds",input.cuisineIds());
        query.put("minLatitude",minLatitude);query.put("maxLatitude",maxLatitude);query.put("minLongitude",minLongitude);query.put("maxLongitude",maxLongitude);
        query.put("zoom",zoom);query.put("clusterX",x);query.put("clusterY",y);
        int total=foodMapper.countClusterMembers(query),size=Math.min(Math.max(pageSize,1),20);
        int p=Math.min(Math.max(page,1),Math.max(1,(total+size-1)/size));query.put("offset",(p-1)*size);query.put("limit",size);
        return new FoodCatalogVO(foodMapper.findClusterMembers(query).stream().map(FoodVO::from).toList(),total,p,size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodVO> listMine(String username) {
        var owner = com.dayan.food.security.AuthenticatedActor.resolve(appUserMapper, username);
        if (owner == null || !owner.isActive()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在或已停用");
        return foodMapper.findMinePage(owner.getId(), 0, 50).stream()
                .map(FoodVO::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodFootprintVO> listRecentVisits(String username, int limit) {
        int normalizedLimit = Math.min(Math.max(limit, 1), 50);
        return foodMapper.findRecentVisits(com.dayan.food.security.AuthenticatedActor.resolve(appUserMapper,username).getId(), normalizedLimit).stream()
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
                        personalized ? com.dayan.food.security.AuthenticatedActor.resolve(appUserMapper,username).getId() : null,
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
        var owner = com.dayan.food.security.AuthenticatedActor.resolve(appUserMapper, username);
        if (owner == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在");
        }
        var existing = foodMapper.findOwnedById(id, owner.getId());
        if (existing == null) {
            throw notFound("只能编辑自己上传的菜品");
        }
        if (request.regionId() != null && regionMapper.findById(request.regionId()) == null) {
            throw notFound("地区不存在");
        }

        // Personal edits require a fresh review, including administrator-owned dishes.
        FoodReviewStatus nextStatus = FoodReviewStatus.PENDING;
        String reviewedBy = null;
        int updated = foodMapper.updateOwnedDetails(
                id,
                owner.getId(),
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
            throw notFound("只能编辑自己上传的菜品");
        }
        if (foodTagService != null && request.tagIds() != null) foodTagService.replaceFoodTags(id, request.tagIds(), username);
        Long previousRegionId = existing.getRegion() == null ? null : existing.getRegion().getId();
        if (!java.util.Objects.equals(previousRegionId, request.regionId())) {
            // Imported labels override region labels; discard them when the region changes.
            foodMapper.updateLocationLabels(id, null, null);
        }
        clearFoodCaches(id);
        return FoodVO.from(foodMapper.findOwnedById(id, owner.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public FoodVO detail(Long id) {
        Food food = foodMapper.findById(id);
        if (food == null) {
            throw notFound("美食不存在");
        }
        return FoodVO.from(food, food.getCreatedByUserId() == null || !"VERIFIED".equals(food.getOwnershipStatus()) ? null : appUserMapper.findById(food.getCreatedByUserId()));
    }

    @Override
    @Transactional
    public void recordVisit(Long id, String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        Long actorId=com.dayan.food.security.AuthenticatedActor.resolveForUpdate(appUserMapper,username).getId();
        if (foodMapper.insertDailyVisit(id, actorId) == 0) {
            // 同一天再次浏览不重复增加热度，但要刷新足迹的最近访问时间。
            foodMapper.touchDailyVisit(id, actorId);
            return;
        }
        if (foodMapper.incrementHeat(id) != 1) {
            throw notFound("美食不存在");
        }

        // S1：详情缓存整体失效（热度即时），但不再清空列表缓存——目录热值的短暂滞后
        // 由 10 分钟 TTL 兜底，避免高频浏览把目录缓存打成永远 miss。
        var detailCache = cacheManager.getCache("foodDetails");
        cacheInvalidator.invalidate(detailCache, id);
    }

    @Override
    @Transactional
    public void review(Long id, FoodReviewStatus status, long expectedVersion, String reviewedBy) {
        reviewedBy=requireAdministrator().getUsername();
        if (status != FoodReviewStatus.APPROVED && status != FoodReviewStatus.REJECTED) {
            throw new IllegalArgumentException("审批结果只能是通过或驳回");
        }
        if (foodMapper.updateReviewStatus(id, status, reviewedBy, expectedVersion) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "待审内容已变化，请刷新后重新审核");
        }
        clearFoodCaches(id);
    }

    @Override
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
        var uploader = com.dayan.food.security.AuthenticatedActor.resolve(appUserMapper, createdBy);
        if (uploader == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在");
        }
        var region = regionId == null ? null : regionMapper.findById(regionId);
        if (regionId != null && region == null) {
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
                uploader.getId(),
                uploader.getRole() == UserRole.ADMIN || uploader.getRole() == UserRole.SUB_ADMIN
                        ? FoodReviewStatus.APPROVED
                        : FoodReviewStatus.PENDING
        );
        foodMapper.insert(food);
        if(discoveryVersion!=null)discoveryVersion.advance();
        // 集合类缓存（前台列表/目录分页）在事务提交后统一失效（BUG-03：回滚不清缓存）。
        cacheInvalidator.clear(cacheManager.getCache("foodLists"));
        cacheInvalidator.clear(cacheManager.getCache("foodCatalogs"));
        cacheInvalidator.clear(cacheManager.getCache("foodMarkers"));
        return FoodVO.from(food);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireAdministrator();
        if (foodMapper.deleteById(id) == 0) {
            throw notFound("美食不存在");
        }
        clearFoodCaches(id);
    }

    @Override
    @Transactional
    public FoodVO create(FoodCreateDTO request, String username) {
        FoodVO created = create(request.name(), request.regionId(), request.latitude(),
                request.longitude(), request.address(), request.summary(), request.story(),
                request.ingredients(), request.imageUrl(), request.remark(), username);
        // 保存选点城市到菜品本身，不调用地区创建逻辑。
        foodMapper.updateLocationLabels(created.id(),
                normalizeOptional(request.province()), normalizeOptional(request.city()));
        if (foodTagService != null) foodTagService.replaceFoodTags(created.id(), request.tagIds(), username);
        var owner = com.dayan.food.security.AuthenticatedActor.resolve(appUserMapper, username);
        return FoodVO.from(foodMapper.findOwnedById(created.id(), owner.getId()));
    }

    private com.dayan.food.entity.po.AppUser requireAdministrator(){
        var actor=com.dayan.food.security.AuthenticatedActor.resolve(appUserMapper,com.dayan.food.security.AuthenticatedActor.principal().username());
        if(actor.getRole()!=UserRole.ADMIN&&actor.getRole()!=UserRole.SUB_ADMIN)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"需要管理员权限");
        return actor;
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
        if(discoveryVersion!=null)discoveryVersion.advance();
        cacheInvalidator.invalidate(cacheManager.getCache("foodDetails"), id);
        cacheInvalidator.clear(cacheManager.getCache("foodLists"));
        cacheInvalidator.clear(cacheManager.getCache("foodCatalogs"));
        cacheInvalidator.clear(cacheManager.getCache("foodMarkers"));
        cacheInvalidator.clear(cacheManager.getCache("foodDiscoveryCatalog"));
        cacheInvalidator.clear(cacheManager.getCache("foodDiscoveryCounts"));
        cacheInvalidator.clear(cacheManager.getCache("foodDiscoveryMap"));
        cacheInvalidator.clear(cacheManager.getCache("wishlistMatches"));
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(keyword.trim(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ");
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("搜索关键词不能超过 100 个字符");
        }
        return normalized;
    }

    private int discoveryCount(List<String> tokens, Long regionId, List<Long> tasteIds,
            List<Long> ingredientIds, List<Long> cuisineIds, BigDecimal minLatitude,
            BigDecimal maxLatitude, BigDecimal minLongitude, BigDecimal maxLongitude) {
        if (discoveryCountService != null) return discoveryCountService.count(tokens, regionId, tasteIds,
                ingredientIds, cuisineIds, minLatitude, maxLatitude, minLongitude, maxLongitude);
        return foodMapper.countFilteredCatalog(tokens, regionId, tasteIds, ingredientIds, cuisineIds,
                minLatitude, maxLatitude, minLongitude, maxLongitude);
    }

    private SearchInput searchInput(String keyword, List<Long> tasteIds, List<Long> ingredientIds,
            List<Long> cuisineIds, String sort, BigDecimal minLatitude, BigDecimal maxLatitude,
            BigDecimal minLongitude, BigDecimal maxLongitude) {
        validateBounds(minLatitude, maxLatitude, minLongitude, maxLongitude);
        String normalized = normalizeKeyword(keyword);
        String normalizedSort = sort == null ? "RELEVANCE" : sort.trim().toUpperCase();
        if (!List.of("RELEVANCE", "HEAT", "NEWEST").contains(normalizedSort)) {
            throw new IllegalArgumentException("排序参数无效");
        }
        List<String> tokens = normalized == null ? List.of() : List.of(normalized.split(" "));
        return new SearchInput(normalized, tokens, ids(tasteIds), ids(ingredientIds),
                ids(cuisineIds), normalizedSort);
    }

    private List<Long> ids(List<Long> values) {
        if (values == null) return List.of();
        List<Long> result = values.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (result.size() > 10) throw new IllegalArgumentException("每个标签维度最多选择 10 项");
        return foodTagService == null ? result : foodTagService.canonicalIds(result);
    }

    private String discoveryKey(Object[] values){
        Object[] normalized=new Object[values.length];
        for(int i=0;i<values.length;i++){
            Object value=values[i];
            if(value instanceof List<?> ids)normalized[i]=ids.stream().map(String::valueOf).sorted().toList();
            else if(value instanceof BigDecimal number)normalized[i]=number.stripTrailingZeros().toPlainString();
            else if(value instanceof String text)normalized[i]=Normalizer.normalize(text,Normalizer.Form.NFKC).trim().replaceAll("\\s+"," ").toLowerCase(java.util.Locale.ROOT);
            else normalized[i]=value;
        }
        return java.util.Arrays.deepToString(normalized);
    }

    private record SearchInput(String keyword, List<String> tokens, List<Long> tasteIds,
            List<Long> ingredientIds, List<Long> cuisineIds, String sort) {}
}
