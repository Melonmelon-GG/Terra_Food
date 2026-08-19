package com.dayan.food.mapper;

import com.dayan.food.entity.po.AppUser;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AppUserMapper {

    AppUser findByUsername(String username);

    AppUser findByUsernameOrDisplayName(String identity);

    int countByUsername(String username);

    AppUser findById(Long id);

    List<AppUser> findPage(@Param("offset") int offset, @Param("pageSize") int pageSize);

    int count();

    int updateActive(@Param("id") Long id, @Param("active") boolean active);

    int deleteById(Long id);

    int claimFoodUpload(String username);

    int insert(AppUser user);
}
