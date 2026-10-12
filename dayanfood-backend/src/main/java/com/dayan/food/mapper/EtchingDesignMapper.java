package com.dayan.food.mapper;

import com.dayan.food.entity.po.EtchingDesign;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EtchingDesignMapper {
    List<EtchingDesign> findByUserId(Long userId);
    EtchingDesign findSelectedByUserId(Long userId);
    EtchingDesign findOwnedById(@Param("id") Long id, @Param("userId") Long userId);
    int countByUserId(Long userId);
    int insert(EtchingDesign design);
    int updateOwned(@Param("id") Long id, @Param("userId") Long userId,
                    @Param("name") String name, @Param("layerOneJson") String layerOneJson);
    int deleteOwned(@Param("id") Long id, @Param("userId") Long userId);
    int clearSelection(Long userId);
    int selectOwned(@Param("id") Long id, @Param("userId") Long userId);
}
