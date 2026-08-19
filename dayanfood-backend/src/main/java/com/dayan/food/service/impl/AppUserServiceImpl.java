package com.dayan.food.service.impl;

import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.service.AppUserService;
import com.dayan.food.entity.vo.AuthUserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AppUserServiceImpl implements AppUserService {

    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;

    public AppUserServiceImpl(AppUserMapper appUserMapper, PasswordEncoder passwordEncoder) {
        this.appUserMapper = appUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void createIfMissing(String username, String rawPassword, String displayName, UserRole role) {
        if (appUserMapper.countByUsername(username) > 0) {
            return;
        }

        // 默认密码只在首次创建时编码入库，数据库中不会保存明文。
        var user = new AppUser(username, passwordEncoder.encode(rawPassword), displayName, role);
        appUserMapper.insert(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuthUserVO> listUsers(int page, int pageSize) {
        int normalizedPage = Math.max(1, page);
        int normalizedPageSize = normalizedPageSize(pageSize);
        int offset = (normalizedPage - 1) * normalizedPageSize;

        return appUserMapper.findPage(offset, normalizedPageSize).stream()
                .map(AuthUserVO::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int countUsers() {
        return appUserMapper.count();
    }

    @Override
    @Transactional
    public void setActive(Long id, boolean active, String operatorUsername) {
        var user = appUserMapper.findById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (user.getUsername().equals(operatorUsername) && !active) {
            throw new IllegalArgumentException("不能停用当前登录管理员");
        }

        int updated = appUserMapper.updateActive(id, active);
        if (updated != 1) {
            throw new IllegalArgumentException("用户状态更新失败");
        }
    }

    @Override
    @Transactional
    public void deleteById(Long id, String operatorUsername) {
        var user = appUserMapper.findById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (user.getUsername().equals(operatorUsername)) {
            throw new IllegalArgumentException("不能删除当前登录管理员");
        }

        int deleted = appUserMapper.deleteById(id);
        if (deleted != 1) {
            throw new IllegalArgumentException("删除用户失败");
        }
    }

    private int normalizedPageSize(int pageSize) {
        return switch (pageSize) {
            case 20, 50 -> pageSize;
            default -> 10;
        };
    }
}
