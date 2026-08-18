package com.dayan.food.service;

import com.dayan.food.entity.po.Food;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.repository.FoodRepository;
import com.dayan.food.repository.RegionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class FoodService {
    private final FoodRepository foodRepository;
    private final RegionRepository regionRepository;

    public FoodService(FoodRepository foodRepository, RegionRepository regionRepository) {
        this.foodRepository = foodRepository;
        this.regionRepository = regionRepository;
    }

    @Transactional(readOnly = true)
    public List<FoodVO> list(String keyword, Long regionId) {
        List<Food> foods;

        if (keyword != null && !keyword.isBlank()) {
            foods = foodRepository.findByNameContainingIgnoreCaseOrderByHeatDesc(keyword.trim());
        } else if (regionId != null) {
            foods = foodRepository.findByRegionIdOrderByHeatDesc(regionId);
        } else {
            foods = foodRepository.findAllByOrderByHeatDesc();
        }

        return foods.stream()
                .map(FoodVO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FoodVO detail(Long id) {
        var food = foodRepository.findById(id)
                .orElseThrow(() -> notFound("美食不存在"));

        return FoodVO.from(food);
    }

    @Transactional
    public FoodVO create(
            String name,
            Long regionId,
            BigDecimal latitude,
            BigDecimal longitude,
            String summary,
            String story,
            String ingredients,
            String imageUrl
    ) {
        var region = regionRepository.findById(regionId)
                .orElseThrow(() -> notFound("地区不存在"));

        var food = new Food(
                name,
                region,
                latitude,
                longitude,
                summary,
                story,
                ingredients,
                imageUrl
        );
        var savedFood = foodRepository.save(food);

        return FoodVO.from(savedFood);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
