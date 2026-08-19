package com.dayan.food.service;

import com.dayan.food.entity.vo.RegionVO;

import java.util.List;

public interface RegionService {

    List<RegionVO> list();

    RegionVO resolveLocation(String province, String city);

}
