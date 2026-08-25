package com.dayan.food.service;

import com.dayan.food.entity.vo.CityCenterVO;

public interface CityCenterService {

    CityCenterVO resolve(String province, String cityText, String addressText, String previousCity);

    CityCenterVO findCenter(String province, String city);

    String normalizeProvince(String province);
}
