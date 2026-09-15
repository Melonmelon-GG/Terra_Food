package com.dayan.food.service;

import com.dayan.food.entity.vo.ImageExportVO;
import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {

    String store(MultipartFile file);

    ImageExportVO readForExport(String imageUrl);

    int cleanupOrphans();
}
