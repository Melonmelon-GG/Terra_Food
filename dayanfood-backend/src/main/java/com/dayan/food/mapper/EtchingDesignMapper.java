package com.dayan.food.mapper;

import com.dayan.food.entity.po.EtchingDesign;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface EtchingDesignMapper {
    List<EtchingDesign> findByUsername(String username);
    EtchingDesign findSelectedByUserId(Long userId);
    EtchingDesign findOwnedById(@Param("id") Long id, @Param("username") String username);
    int countByUsername(String username);
    int insert(EtchingDesign design);
    int updateOwned(@Param("id") Long id, @Param("username") String username,
                    @Param("name") String name, @Param("layerOneJson") String layerOneJson);
    int deleteOwned(@Param("id") Long id, @Param("username") String username);
    int clearSelection(String username);
    int selectOwned(@Param("id") Long id, @Param("username") String username);
}
