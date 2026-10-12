package com.dayan.food.service.impl;

import com.dayan.food.entity.dto.FoodCreateDTO;
import com.dayan.food.entity.vo.FoodVO;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.FoodIdempotencyMapper;
import com.dayan.food.service.FoodCreationService;
import com.dayan.food.service.FoodService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class FoodCreationServiceImpl implements FoodCreationService {
    private final FoodService foodService;
    private final AppUserMapper appUserMapper;
    private final FoodIdempotencyMapper idempotencyMapper;
    private final ObjectMapper objectMapper;

    public FoodCreationServiceImpl(FoodService foodService, AppUserMapper appUserMapper,
                                   FoodIdempotencyMapper idempotencyMapper, ObjectMapper objectMapper) {
        this.foodService = foodService;
        this.appUserMapper = appUserMapper;
        this.idempotencyMapper = idempotencyMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public FoodVO create(FoodCreateDTO request, String username, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return foodService.create(request, username);
        String key = idempotencyKey.trim();
        if (key.length() < 8 || key.length() > 100) throw new IllegalArgumentException("幂等键长度必须为 8 到 100 个字符");
        // Use a locking read as the first database read in this transaction.
        // A non-locking read first would establish an older repeatable-read
        // snapshot, allowing a waiter to miss the winner's committed result.
        var user = com.dayan.food.security.AuthenticatedActor.resolveForUpdate(appUserMapper, username);
        if (user == null || !user.isActive()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录用户不存在或已停用");
        String requestHash = hash(request);
        LocalDateTime now = LocalDateTime.now();
        idempotencyMapper.deleteExpired(user.getId(), key, now);
        var existing = idempotencyMapper.find(user.getId(), key);
        if (existing != null) return replay(existing, requestHash, username);
        idempotencyMapper.insertReservation(user.getId(), key, requestHash, now.plusHours(24));
        FoodVO created = foodService.create(request, username);
        if (idempotencyMapper.attachFood(user.getId(), key, created.id()) != 1) {
            throw new IllegalStateException("菜品幂等结果保存失败");
        }
        return created;
    }

    private FoodVO replay(com.dayan.food.entity.po.FoodCreateIdempotency existing,
                          String requestHash, String username) {
        if (!requestHash.equals(existing.getRequestHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同一幂等键不能用于不同的菜品内容");
        }
        if (existing.getFoodId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "相同请求正在处理中，请稍后重试");
        }
        return foodService.ownedDetail(existing.getFoodId(), username);
    }

    private String hash(FoodCreateDTO request) {
        try {
            byte[] json = objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法生成提交摘要", exception);
        }
    }
}
