package com.dayan.food.service.impl;

import com.dayan.food.entity.enums.ReviewField;
import com.dayan.food.entity.enums.ReviewStatus;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.po.UserReviewItem;
import com.dayan.food.entity.vo.PendingReviewVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.UserReviewItemMapper;
import com.dayan.food.service.AppUserService;
import com.dayan.food.service.UserReviewPresenter;
import com.dayan.food.entity.vo.AuthUserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AppUserServiceImpl implements AppUserService {

    private final AppUserMapper appUserMapper;
    private final UserReviewItemMapper userReviewItemMapper;
    private final UserReviewPresenter reviewPresenter;
    private final PasswordEncoder passwordEncoder;

    public AppUserServiceImpl(
            AppUserMapper appUserMapper,
            UserReviewItemMapper userReviewItemMapper,
            UserReviewPresenter reviewPresenter,
            PasswordEncoder passwordEncoder
    ) {
        this.appUserMapper = appUserMapper;
        this.userReviewItemMapper = userReviewItemMapper;
        this.reviewPresenter = reviewPresenter;
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
        // 页号设上限，避免极端页码导致 (page-1)*pageSize 整数溢出。
        int normalizedPage = Math.min(Math.max(page, 1), 20_000);
        int normalizedPageSize = normalizedPageSize(pageSize);
        int offset = (normalizedPage - 1) * normalizedPageSize;

        List<AppUser> users = appUserMapper.findPage(offset, normalizedPageSize);
        List<Long> userIds = users.stream().map(AppUser::getId).toList();
        Map<Long, List<PendingReviewVO>> pendingByUser = userIds.isEmpty()
                ? Map.of()
                : userReviewItemMapper.findPendingByIds(userIds).stream()
                        .collect(Collectors.groupingBy(UserReviewItem::getUserId,
                                Collectors.mapping(PendingReviewVO::from, Collectors.toList())));

        return users.stream()
                .map(user -> AuthUserVO.from(user, pendingByUser.getOrDefault(user.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int countUsers() {
        return appUserMapper.count();
    }

    @Override
    @CacheEvict(cacheNames = "authUsers", key = "#username")
    @Transactional
    public AuthUserVO updateAvatar(String username, String avatarUrl) {
        String normalizedAvatarUrl = avatarUrl.trim();
        if (appUserMapper.updateAvatar(username, normalizedAvatarUrl) != 1) {
            throw new IllegalArgumentException("当前用户不存在或已被停用");
        }
        return toAuthUserVO(findRequiredByUsername(username));
    }

    @Override
    @CacheEvict(cacheNames = "authUsers", key = "#username")
    @Transactional
    public AuthUserVO updateSignature(String username, String signature) {
        AppUser user = findRequiredByUsername(username);
        String normalized = signature.trim();
        // 签名进入通用待审：currentValue 记录当前生效值，pendingValue 为待审文本。
        userReviewItemMapper.upsertPending(user.getId(), ReviewField.SIGNATURE, currentText(user.getSignature()), normalized);
        return toAuthUserVO(user);
    }

    @Override
    @CacheEvict(cacheNames = "authUsers", key = "#username")
    @Transactional
    public AuthUserVO submitDisplayName(String username, String displayName) {
        AppUser user = findRequiredByUsername(username);
        String normalized = displayName.trim();
        userReviewItemMapper.upsertPending(user.getId(), ReviewField.DISPLAY_NAME, currentText(user.getDisplayName()), normalized);
        return toAuthUserVO(user);
    }

    @Override
    @CacheEvict(cacheNames = "authUsers", allEntries = true)
    @Transactional
    public void reviewItem(Long userId, ReviewField field, ReviewStatus status, String operatorUsername) {
        if (status != ReviewStatus.APPROVED && status != ReviewStatus.REJECTED) {
            throw new IllegalArgumentException("审批结果只能是通过或驳回");
        }

        var operator = findRequiredOperator(operatorUsername);
        var user = findRequiredUser(userId);
        ensureCanManageTarget(operator, user);

        UserReviewItem item = userReviewItemMapper.findPendingByUserAndField(userId, field);
        if (item == null) {
            throw new IllegalArgumentException("该待审内容不存在或已经处理");
        }

        if (status == ReviewStatus.APPROVED) {
            applyApprovedValue(user, item);
        }

        int updated = status == ReviewStatus.APPROVED
                ? userReviewItemMapper.approveItem(item.getId(), operatorUsername)
                : userReviewItemMapper.rejectItem(item.getId(), operatorUsername);
        // WHERE status='PENDING' 保障并发下只生效一次。
        if (updated != 1) {
            throw new IllegalArgumentException("该待审内容不存在或已经处理");
        }
    }

    private void applyApprovedValue(AppUser user, UserReviewItem item) {
        String value = item.getPendingValue().trim();
        switch (item.getField()) {
            case SIGNATURE -> {
                if (appUserMapper.updateSignature(user.getId(), value) != 1) {
                    throw new IllegalArgumentException("用户状态已变化，请刷新后重试");
                }
            }
            case DISPLAY_NAME -> {
                // 昵称同时是登录别名：通过后若与他人昵称冲突会造成登录歧义，审批前校验。
                AppUser conflict = appUserMapper.findByUsernameOrDisplayName(value);
                if (conflict != null && !conflict.getId().equals(user.getId())) {
                    throw new IllegalArgumentException("该昵称已被其他用户使用");
                }
                if (appUserMapper.updateDisplayName(user.getId(), value) != 1) {
                    throw new IllegalArgumentException("用户状态已变化，请刷新后重试");
                }
            }
            case SEAL -> throw new IllegalArgumentException("该字段的审核尚未开放");
        }
    }

    @Override
    @CacheEvict(cacheNames = "authUsers", allEntries = true)
    @Transactional
    public void setActive(Long id, boolean active, String operatorUsername) {
        var operator = findRequiredOperator(operatorUsername);
        var user = findRequiredUser(id);
        ensureCanManageTarget(operator, user);

        if (user.getUsername().equals(operatorUsername) && !active) {
            throw new IllegalArgumentException("不能停用当前登录管理员");
        }

        int updated = appUserMapper.updateActive(id, active);
        if (updated != 1) {
            throw new IllegalArgumentException("用户状态更新失败");
        }
    }

    @Override
    @CacheEvict(cacheNames = "authUsers", allEntries = true)
    @Transactional
    public void setRole(Long id, UserRole role, String operatorUsername) {
        if (role != UserRole.USER && role != UserRole.SUB_ADMIN) {
            throw new IllegalArgumentException("只能在普通用户和子管理员之间调整角色");
        }

        var operator = findRequiredOperator(operatorUsername);
        if (operator.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("只有主管理员可以调整用户角色");
        }

        var user = findRequiredUser(id);
        if (user.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("不能修改主管理员角色");
        }

        int updated = appUserMapper.updateRole(id, role);
        if (updated != 1) {
            throw new IllegalArgumentException("用户角色更新失败");
        }
    }

    @Override
    @CacheEvict(cacheNames = "authUsers", allEntries = true)
    @Transactional
    public void deleteById(Long id, String operatorUsername) {
        var operator = findRequiredOperator(operatorUsername);
        var user = findRequiredUser(id);
        ensureCanManageTarget(operator, user);

        if (user.getUsername().equals(operatorUsername)) {
            throw new IllegalArgumentException("不能删除当前登录管理员");
        }

        // user_review_item 由外键 ON DELETE CASCADE 一并清理。
        int deleted = appUserMapper.deleteById(id);
        if (deleted != 1) {
            throw new IllegalArgumentException("删除用户失败");
        }
    }

    private AuthUserVO toAuthUserVO(AppUser user) {
        return reviewPresenter.toVO(user);
    }

    private AppUser findRequiredByUsername(String username) {
        AppUser user = appUserMapper.findByUsername(username);
        if (user == null || !user.isActive()) {
            throw new IllegalArgumentException("当前用户不存在或已被停用");
        }
        return user;
    }

    private AppUser findRequiredUser(Long id) {
        var user = appUserMapper.findById(id);
        if (user == null || !user.isActive()) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private AppUser findRequiredOperator(String username) {
        var operator = appUserMapper.findByUsername(username);
        if (operator == null) {
            throw new IllegalArgumentException("当前管理员不存在");
        }
        return operator;
    }

    private void ensureCanManageTarget(AppUser operator, AppUser target) {
        if (operator.getRole() == UserRole.SUB_ADMIN && target.getRole() != UserRole.USER) {
            throw new IllegalArgumentException("子管理员不能管理其他管理员");
        }
    }

    private String currentText(String value) {
        return value == null ? "" : value;
    }

    private int normalizedPageSize(int pageSize) {
        return switch (pageSize) {
            case 20, 50 -> pageSize;
            default -> 10;
        };
    }
}
