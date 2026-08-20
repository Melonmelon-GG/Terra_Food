package com.dayan.food.config;

import com.dayan.food.service.ImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrphanImageCleanupTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrphanImageCleanupTask.class);

    private final ImageStorageService imageStorageService;

    public OrphanImageCleanupTask(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @Scheduled(cron = "${app.upload.cleanup-cron:0 30 3 * * *}")
    public void cleanup() {
        try {
            int deleted = imageStorageService.cleanupOrphans();
            if (deleted > 0) {
                LOGGER.info("已清理 {} 个未关联菜品的过期上传文件", deleted);
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("未关联上传文件清理失败，将在下个周期重试", exception);
        }
    }
}
