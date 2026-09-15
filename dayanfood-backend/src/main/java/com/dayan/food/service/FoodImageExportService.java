package com.dayan.food.service;

import com.dayan.food.entity.vo.ImageExportVO;

public interface FoodImageExportService {

    ImageExportVO read(Long foodId);
}
