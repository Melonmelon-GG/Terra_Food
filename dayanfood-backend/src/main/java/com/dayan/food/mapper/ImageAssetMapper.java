package com.dayan.food.mapper;

import com.dayan.food.entity.po.ImageAsset;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface ImageAssetMapper {
    int insert(ImageAsset asset);
    ImageAsset findByOriginalUrl(@Param("originalUrl") String originalUrl);
    List<ImageAsset> findPending(@Param("limit") int limit);
    int claim(@Param("id") Long id, @Param("owner") String owner);
    int readyOwned(@Param("id") Long id, @Param("owner") String owner,
        @Param("url320") String url320, @Param("url640") String url640, @Param("url1280") String url1280);
    int failOwned(@Param("id") Long id, @Param("owner") String owner, @Param("errorCode") String errorCode);
    int recoverExpired();
    int markReady(@Param("id") Long id, @Param("url320") String url320,
                  @Param("url640") String url640, @Param("url1280") String url1280);
    int markFailed(@Param("id") Long id, @Param("errorCode") String errorCode);

    int retryFailed(@Param("id") Long id);
    int deleteByOriginalUrl(@Param("originalUrl") String originalUrl);
}
