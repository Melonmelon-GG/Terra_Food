package com.dayan.food.service.impl;

import com.dayan.food.entity.po.Region;
import com.dayan.food.mapper.RegionMapper;
import com.dayan.food.service.CityCenterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionServiceImplTests {

    @Mock
    private RegionMapper regionMapper;

    @Mock
    private CityCenterService cityCenterService;

    private RegionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RegionServiceImpl(regionMapper, cityCenterService);
    }

    @Test
    void resolveLocationReusesExistingRegion() {
        Region existing = new Region("成都", "四川", "四川 · 成都地方美食");
        when(cityCenterService.normalizeProvince("四川省")).thenReturn("四川");
        when(regionMapper.findByNameAndProvince("成都", "四川")).thenReturn(existing);

        var result = service.resolveLocation("四川省", "成都市");

        assertEquals("成都", result.name());
        assertEquals("四川", result.province());
        verify(regionMapper, never()).insert(any());
    }

    @Test
    void resolveLocationCreatesMissingRegion() {
        when(cityCenterService.normalizeProvince("浙江省")).thenReturn("浙江");
        when(regionMapper.findByNameAndProvince("宁波", "浙江")).thenReturn(null);

        var result = service.resolveLocation("浙江省", "宁波市");

        ArgumentCaptor<Region> captor = ArgumentCaptor.forClass(Region.class);
        verify(regionMapper).insert(captor.capture());
        assertEquals("宁波", captor.getValue().getName());
        assertEquals("浙江", result.province());
        assertEquals("宁波", result.name());
    }

    @Test
    void resolveLocationReusesConcurrentInsert() {
        Region concurrent = new Region("宁波", "浙江", "浙江 · 宁波地方美食");
        when(cityCenterService.normalizeProvince("浙江省")).thenReturn("浙江");
        when(regionMapper.findByNameAndProvince("宁波", "浙江"))
                .thenReturn(null)
                .thenReturn(concurrent);
        when(regionMapper.insert(any(Region.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        var result = service.resolveLocation("浙江省", "宁波市");

        assertEquals("宁波", result.name());
        verify(regionMapper, times(2)).findByNameAndProvince("宁波", "浙江");
    }
}
