package com.dayan.food.service;

import com.dayan.food.entity.enums.ReviewField;
import com.dayan.food.entity.enums.ReviewStatus;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.vo.AuthUserVO;

import java.util.List;

public interface AppUserService {

    void createIfMissing(String username, String rawPassword, String displayName, UserRole role);

    List<AuthUserVO> listUsers(int page, int pageSize);

    int countUsers();

    AuthUserVO updateAvatar(String username, String avatarUrl);

    AuthUserVO updateSignature(String username, String signature);

    AuthUserVO submitDisplayName(String username, String displayName);

    void reviewItem(Long userId, ReviewField field, ReviewStatus status, String operatorUsername);

    void setActive(Long id, boolean active, String operatorUsername);

    void setRole(Long id, UserRole role, String operatorUsername);

    void deleteById(Long id, String operatorUsername);
}