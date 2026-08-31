package com.dayan.food.mapper;

import com.dayan.food.entity.po.AppUser;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AppUserMapper {

    AppUser findByUsername(String username);

    AppUser findByUsernameOrEmail(String identity);

    AppUser findByUsernameOrDisplayName(String identity);

    int countByUsername(String username);

    int countByEmail(String email);

    AppUser findById(Long id);

    /**
     * 公开页专用摘要查询：只取公开字段，password 等敏感列不进内存。
     */
    AppUser findPublicById(@Param("id") Long id);

    List<AppUser> findPage(@Param("offset") int offset, @Param("pageSize") int pageSize);

    int count();

    int countByAvatarUrl(String avatarUrl);

    int updateAvatar(@Param("username") String username, @Param("avatarUrl") String avatarUrl);

    int updateSignature(@Param("id") Long id, @Param("signature") String signature);

    int updateDisplayName(@Param("id") Long id, @Param("displayName") String displayName);

    int updateActive(@Param("id") Long id, @Param("active") boolean active);

    int updateRole(@Param("id") Long id, @Param("role") com.dayan.food.entity.enums.UserRole role);

    int updatePassword(
            @Param("username") String username,
            @Param("email") String email,
            @Param("password") String password
    );

    int deleteById(Long id);

    int insert(AppUser user);
}
