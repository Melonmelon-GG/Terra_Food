package com.dayan.food.service.impl;

import com.dayan.food.entity.po.Region;
import com.dayan.food.entity.vo.RegionVO;
import com.dayan.food.mapper.RegionMapper;
import com.dayan.food.service.CityCenterService;
import com.dayan.food.service.RegionService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RegionServiceImpl implements RegionService {

    private final RegionMapper regionMapper;
    private final CityCenterService cityCenterService;

    public RegionServiceImpl(RegionMapper regionMapper, CityCenterService cityCenterService) {
        this.regionMapper = regionMapper;
        this.cityCenterService = cityCenterService;
    }

    @Override
    @Cacheable(cacheNames = "regions", key = "'all'")
    @Transactional(readOnly = true)
    public List<RegionVO> list() {
        return regionMapper.findAll().stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RegionVO resolveLocation(String province, String city) {
        String normalizedProvince = cityCenterService.normalizeProvince(province);
        String normalizedCity = normalizeCity(city);
        if (normalizedProvince.isBlank() || normalizedCity.isBlank()) {
            throw new IllegalArgumentException("地图未返回可用的省市信息");
        }

        // 地图反编码只做查询，不把用户点选动态写入地区表：地区数据以城市白名单
        // 与既有 region 为准，避免普通用户通过任意坐标持续新增地区（SEC-11）。
        Region region = regionMapper.findByNameAndProvince(normalizedCity, normalizedProvince);
        if (region == null) {
            throw new IllegalArgumentException("该地区尚未收录");
        }
        return toVO(region);
    }

    private RegionVO toVO(Region region) {
        return RegionVO.from(region, cityCenterService.findCenter(region.getProvince(), region.getName()));
    }

    private String normalizeCity(String city) {
        return city == null ? "" : city.trim()
                .replace("自治州", "")
                .replace("地区", "")
                .replace("县", "")
                .replace("区", "")
                .replace("市", "");
    }
}
