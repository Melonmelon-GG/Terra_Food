package com.dayan.food.mapper;

import com.dayan.food.entity.po.Region;

import java.util.List;

public interface RegionMapper {

    List<Region> findAll();

    Region findById(Long id);

    Region findByNameAndProvince(String name, String province);

    int insert(Region region);
}
