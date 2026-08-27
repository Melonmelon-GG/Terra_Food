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

    List<AppUser> findPage(@Param("offset") int offset, @Param("pageSize") int pageSize);

    int count();

    int countByAvatarUrl(String avatarUrl);

    int updateAvatar(@Param("username") String username, @Param("avatarUrl") String avatarUrl);

    int updateSignature(@Param("username") String username, @Param("signature") String signature);

    int approveSignature(@Param("id") Long id);

    int rejectSignature(@Param("id") Long id);

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
