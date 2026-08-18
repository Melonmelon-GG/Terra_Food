package com.dayan.food.repository;

import com.dayan.food.entity.po.Food;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodRepository extends JpaRepository<Food, Long> {

    List<Food> findByNameContainingIgnoreCaseOrderByHeatDesc(String keyword);

    List<Food> findByRegionIdOrderByHeatDesc(Long regionId);

    List<Food> findAllByOrderByHeatDesc();
}
