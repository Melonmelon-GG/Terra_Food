package com.dayan.food.repository;

import com.dayan.food.entity.po.Region;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {
}
