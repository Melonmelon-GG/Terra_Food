package com.dayan.food.service.impl;

import com.dayan.food.entity.dto.FoodCheckinCreateDTO;
import com.dayan.food.entity.dto.FoodCheckinUpdateDTO;
import com.dayan.food.entity.po.FoodCheckin;
import com.dayan.food.entity.vo.FoodCheckinPageVO;
import com.dayan.food.entity.vo.FoodCheckinVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodCheckinMapper;
import com.dayan.food.mapper.FoodMapper;
import com.dayan.food.mapper.FoodCommentMapper;
import com.dayan.food.service.FoodCheckinService;
import com.dayan.food.service.FoodCommentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class FoodCheckinServiceImpl implements FoodCheckinService {
    private final FoodCheckinMapper mapper;
    private final FoodMapper foods;
    private final AppUserMapper users;
    private final FoodCommentService comments;
    private final FoodCommentMapper commentMapper;

    public FoodCheckinServiceImpl(FoodCheckinMapper mapper, FoodMapper foods, AppUserMapper users,
                                  FoodCommentService comments, FoodCommentMapper commentMapper) {
        this.mapper = mapper;
        this.foods = foods;
        this.users = users;
        this.comments = comments;
        this.commentMapper = commentMapper;
    }
    @Override @Transactional
    public FoodCheckinVO create(Long foodId, FoodCheckinCreateDTO request, String username, String idempotencyKey) {
        // The first database read locks the stable actor: no stale RR snapshot before admission.
        var user = com.dayan.food.security.AuthenticatedActor.resolveForUpdate(users, username);
        var food = foods.findById(foodId);
        if (food == null || food.getReviewStatus() != com.dayan.food.entity.enums.FoodReviewStatus.APPROVED) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "美食不存在");
        String timezone = timezone(request.normalizedTimezone());
        if (request.eatenOn().isAfter(LocalDate.now(ZoneId.of(timezone)))) throw new IllegalArgumentException("打卡日期不能晚于今天");
        String note = request.note() == null ? null : request.note().trim();
        String visibility = request.normalizedVisibility();
        String key = normalizeKey(idempotencyKey);
        String hash = requestHash(foodId, request.eatenOn(), note, visibility, timezone);
        if (key != null) {
            String existingHash = mapper.findIdempotentHash(user.getId(), key);
            if (existingHash != null && !existingHash.equals(hash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "同一幂等标识不能用于不同的打卡内容");
            }
            if (existingHash != null) {
                Long resultId = mapper.findIdempotentResult(user.getId(), key, hash);
                var existing = resultId == null ? null : mapper.findOwnedForUpdate(resultId, user.getId());
                if (existing != null) return FoodCheckinVO.from(existing);
                throw new ResponseStatusException(HttpStatus.GONE, "原打卡已删除，此提交标识不能再次发布；请开始新的打卡");
            }
            mapper.deleteExpiredIdempotency(user.getId(), key);
            mapper.insertIdempotency(user.getId(), key, hash);
        }
        Long commentId = null;
        if ("PUBLIC".equals(visibility)) {
            commentId = comments.create(foodId, publicText(note), username).id();
        }
        var checkin = new FoodCheckin(foodId, user.getId(), food.getName(), request.eatenOn(), note,
                visibility, commentId, timezone);
        if (mapper.insert(checkin) != 1 || checkin.getId() == null) throw new IllegalStateException("打卡保存失败");
        if (key != null && mapper.attachIdempotentResult(user.getId(), key, checkin.getId()) != 1) {
            throw new IllegalStateException("打卡幂等结果保存失败");
        }
        return FoodCheckinVO.from(mapper.findOwned(checkin.getId(), user.getId()));
    }
    @Override @Transactional(readOnly = true)
    public FoodCheckinPageVO listMine(String username, int page, int pageSize, String visibility, LocalDate from, LocalDate to) {
        var user = requireUser(username);
        String normalizedVisibility = validateVisibilityFilter(visibility);
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("开始日期不能晚于结束日期");
        int size = Math.min(Math.max(pageSize, 1), 50);
        int total = mapper.countByUser(user.getId(), normalizedVisibility, from, to);
        int pages = Math.max(1, (int) Math.ceil((double) total / size));
        int normalizedPage = Math.min(Math.max(page, 1), pages);
        List<FoodCheckinVO> items = mapper.findByUser(user.getId(), normalizedVisibility, from, to,
                        (normalizedPage - 1) * size, size).stream().map(FoodCheckinVO::from).toList();
        return new FoodCheckinPageVO(items, total, normalizedPage, size);
    }

    @Override @Transactional(readOnly = true)
    public FoodCheckinVO getMine(Long id, String username) {
        var user = requireUser(username);
        var checkin = mapper.findOwned(id, user.getId());
        if (checkin == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "打卡不存在");
        return FoodCheckinVO.from(checkin);
    }

    @Override @Transactional
    public FoodCheckinVO updateMine(Long id, FoodCheckinUpdateDTO request, String username) {
        var user = requireUser(username);
        String timezone = timezone(request.normalizedTimezone());
        if (request.eatenOn().isAfter(LocalDate.now(ZoneId.of(timezone)))) throw new IllegalArgumentException("打卡日期不能晚于今天");
        var current = mapper.findOwnedForUpdate(id, user.getId());
        if (current == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "打卡不存在");
        String note = request.note() == null ? null : request.note().trim();
        Long commentId = current.getCommentId();
        boolean wasPublic = "PUBLIC".equals(current.getVisibility());
        boolean willBePublic = "PUBLIC".equals(request.visibility());
        if (wasPublic && willBePublic && commentId != null) {
            if (commentMapper.updateOwned(commentId, user.getId(), publicText(note)) != 1) throw new IllegalStateException("公开打卡同步失败");
        } else if (wasPublic && !willBePublic && commentId != null) {
            if (commentMapper.deleteOwned(commentId, user.getId()) != 1) throw new IllegalStateException("公开打卡撤回失败");
            commentId = null;
        } else if (!wasPublic && willBePublic) {
            if (current.getFoodId() == null) throw new IllegalArgumentException("原菜品已不可用，无法公开这条打卡");
            commentId = comments.create(current.getFoodId(), publicText(note), username).id();
        }
        if (mapper.updateOwned(id, user.getId(), request.eatenOn(), note, request.visibility(), commentId,
                timezone, request.version()) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "打卡已在其他页面修改，请刷新后重试");
        }
        return FoodCheckinVO.from(mapper.findOwned(id, user.getId()));
    }
    @Override @Transactional
    public void deleteMine(Long id, int expectedVersion, String username) {
        var user = requireUser(username);
        var current = mapper.findOwnedForUpdate(id, user.getId());
        if (current == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "打卡不存在");
        if (current.getVersion() != expectedVersion) throw new ResponseStatusException(HttpStatus.CONFLICT, "打卡已在其他页面修改，请刷新后重试");
        if (mapper.deleteOwned(id, user.getId(), expectedVersion) != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "打卡已在其他页面修改，请刷新后重试");
        if (current.getCommentId() != null && commentMapper.deleteOwned(current.getCommentId(), user.getId()) != 1) {
            throw new IllegalStateException("公开打卡删除失败");
        }
    }

    private com.dayan.food.entity.po.AppUser requireUser(String username) {
        var user = com.dayan.food.security.AuthenticatedActor.resolve(users, username);
        if (user == null || !user.isActive()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在或已停用");
        return user;
    }

    private String validateVisibilityFilter(String visibility) {
        if (visibility == null || visibility.isBlank()) return null;
        String normalized = visibility.trim().toUpperCase();
        if (!"PUBLIC".equals(normalized) && !"PRIVATE".equals(normalized)) throw new IllegalArgumentException("可见性必须为 PUBLIC 或 PRIVATE");
        return normalized;
    }

    private String publicText(String note) {
        return note == null || note.isBlank() ? "已打卡这道菜" : note;
    }

    private String timezone(String value) {
        try { return ZoneId.of(value).getId(); }
        catch (DateTimeException exception) { throw new IllegalArgumentException("时区无效"); }
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 100 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("幂等标识格式无效");
        }
        return normalized;
    }

    private String requestHash(Long foodId, LocalDate eatenOn, String note, String visibility, String timezone) {
        String canonical = foodId + "\n" + eatenOn + "\n" + (note == null ? "" : note) + "\n" + visibility + "\n" + timezone;
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
