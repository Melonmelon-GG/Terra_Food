package com.dayan.food.service;

import com.dayan.food.entity.vo.FoodImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface FoodImportService {

    FoodImportResultVO importSpreadsheet(MultipartFile file);
}
