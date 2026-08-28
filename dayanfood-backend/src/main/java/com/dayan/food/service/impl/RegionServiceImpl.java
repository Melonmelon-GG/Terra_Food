package com.dayan.food.service.impl;

import com.dayan.food.entity.po.Region;
import com.dayan.food.entity.vo.RegionVO;
import com.dayan.food.mapper.RegionMapper;
import com.dayan.food.service.CityCenterService;
import com.dayan.food.service.RegionService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
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
    @CacheEvict(cacheNames = "regions", allEntries = true)
    @Transactional
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

        Region region = regionMapper.findByNameAndProvince(normalizedCity, normalizedProvince);
        if (region == null) {
            region = new Region(
                    normalizedCity,
                    normalizedProvince,
                    normalizedProvince + " · " + normalizedCity + "地方美食"
            );
            try {
                regionMapper.insert(region);
            } catch (DuplicateKeyException exception) {
                // 两个登录用户同时首次收录同一城市时，复用另一事务刚创建的记录。
                region = regionMapper.findByNameAndProvince(normalizedCity, normalizedProvince);
                if (region == null) throw exception;
            }
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
