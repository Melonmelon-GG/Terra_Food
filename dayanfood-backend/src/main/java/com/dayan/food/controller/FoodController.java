package com.dayan.food.controller;

import com.dayan.food.entity.dto.FoodCreateDTO;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.entity.vo.FoodCatalogVO;
import com.dayan.food.entity.vo.FoodImportResultVO;
import com.dayan.food.entity.vo.FoodMarkerVO;
import com.dayan.food.entity.vo.FoodMapResultsVO;
import com.dayan.food.entity.vo.FoodMapClustersVO;
import com.dayan.food.service.FoodImportService;
import com.dayan.food.service.FoodCreationService;
import com.dayan.food.service.FoodService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/foods")
public class FoodController {

    private final FoodService foodService;
    private final FoodImportService foodImportService;
    private final FoodCreationService foodCreationService;

    public FoodController(FoodService foodService, FoodImportService foodImportService, FoodCreationService foodCreationService) {
        this.foodService = foodService;
        this.foodImportService = foodImportService;
        this.foodCreationService = foodCreationService;
    }

    @GetMapping
    public List<FoodVO> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) BigDecimal minLatitude,
            @RequestParam(required = false) BigDecimal maxLatitude,
            @RequestParam(required = false) BigDecimal minLongitude,
            @RequestParam(required = false) BigDecimal maxLongitude
    ) {
        return foodService.list(
                keyword,
                regionId,
                minLatitude,
                maxLatitude,
                minLongitude,
                maxLongitude
        );
    }

    @GetMapping("/markers")
    public List<FoodMarkerVO> markers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) BigDecimal minLatitude,
            @RequestParam(required = false) BigDecimal maxLatitude,
            @RequestParam(required = false) BigDecimal minLongitude,
            @RequestParam(required = false) BigDecimal maxLongitude
    ) {
        return foodService.markers(
                keyword,
                regionId,
                minLatitude,
                maxLatitude,
                minLongitude,
                maxLongitude
        );
    }

    @GetMapping("/catalog")
    public FoodCatalogVO catalog(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) List<Long> tasteIds,
            @RequestParam(required = false) List<Long> ingredientIds,
            @RequestParam(required = false) List<Long> cuisineIds,
            @RequestParam(defaultValue = "RELEVANCE") String sort,
            @RequestParam(defaultValue = "false") boolean inBounds,
            @RequestParam(required = false) BigDecimal minLatitude,
            @RequestParam(required = false) BigDecimal maxLatitude,
            @RequestParam(required = false) BigDecimal minLongitude,
            @RequestParam(required = false) BigDecimal maxLongitude,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int pageSize,
            @RequestParam(defaultValue = "false") boolean compact
    ) {
        return foodService.filteredCatalog(keyword, regionId, tasteIds, ingredientIds, cuisineIds,
                sort, inBounds ? minLatitude : null, inBounds ? maxLatitude : null,
                inBounds ? minLongitude : null, inBounds ? maxLongitude : null, page, pageSize, compact);
    }

    @GetMapping("/map-results")
    public FoodMapResultsVO mapResults(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) List<Long> tasteIds,
            @RequestParam(required = false) List<Long> ingredientIds,
            @RequestParam(required = false) List<Long> cuisineIds,
            @RequestParam(defaultValue = "RELEVANCE") String sort,
            @RequestParam(defaultValue = "false") boolean inBounds,
            @RequestParam(required = false) BigDecimal minLatitude,
            @RequestParam(required = false) BigDecimal maxLatitude,
            @RequestParam(required = false) BigDecimal minLongitude,
            @RequestParam(required = false) BigDecimal maxLongitude) {
        return foodService.filteredMap(keyword, regionId, tasteIds, ingredientIds, cuisineIds, sort,
                inBounds ? minLatitude : null, inBounds ? maxLatitude : null,
                inBounds ? minLongitude : null, inBounds ? maxLongitude : null);
    }

    @GetMapping("/map-clusters")
    public FoodMapClustersVO mapClusters(
            @RequestParam(required = false) String keyword, @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) List<Long> tasteIds, @RequestParam(required = false) List<Long> ingredientIds,
            @RequestParam(required = false) List<Long> cuisineIds,
            @RequestParam BigDecimal minLatitude, @RequestParam BigDecimal maxLatitude,
            @RequestParam BigDecimal minLongitude, @RequestParam BigDecimal maxLongitude,
            @RequestParam(defaultValue = "4") int zoom) {
        return foodService.mapClusters(keyword, regionId, tasteIds, ingredientIds, cuisineIds,
                minLatitude, maxLatitude, minLongitude, maxLongitude, zoom);
    }

    @GetMapping("/map-clusters/{clusterId}/members")
    public FoodCatalogVO mapClusterMembers(
            @PathVariable String clusterId,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) List<Long> tasteIds, @RequestParam(required = false) List<Long> ingredientIds,
            @RequestParam(required = false) List<Long> cuisineIds,
            @RequestParam BigDecimal minLatitude, @RequestParam BigDecimal maxLatitude,
            @RequestParam BigDecimal minLongitude, @RequestParam BigDecimal maxLongitude,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return foodService.mapClusterMembers(clusterId, keyword, regionId, tasteIds, ingredientIds, cuisineIds,
                minLatitude, maxLatitude, minLongitude, maxLongitude, page, pageSize);
    }

    @GetMapping("/{id}")
    public FoodVO detail(@PathVariable Long id, Authentication authentication) {
        // 游客浏览（匿名 token 的 isAuthenticated 为 false）不计入热度与每日访问。
        if (authentication != null && authentication.isAuthenticated()) {
            foodService.recordVisit(id, authentication.getName());
        }
        return foodService.detail(id);
    }

    @GetMapping("/mine/page")
    public FoodCatalogVO minePage(Authentication authentication,@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize){
        return foodService.listMinePage(authentication.getName(),page,pageSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FoodVO create(@Valid @RequestBody FoodCreateDTO request, Authentication authentication, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return foodCreationService.create(request, authentication.getName(), idempotencyKey);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public FoodImportResultVO importSpreadsheet(@RequestParam("file") MultipartFile file) {
        return foodImportService.importSpreadsheet(file);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        foodService.delete(id);
    }
}
