package com.dayan.food.service.impl;

import com.dayan.food.entity.dto.EtchingDesignDTO;
import com.dayan.food.entity.po.EtchingDesign;
import com.dayan.food.entity.vo.EtchingDesignVO;
import com.dayan.food.mapper.AchievementMapper;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.EtchingDesignMapper;
import com.dayan.food.service.EtchingDesignService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EtchingDesignServiceImpl implements EtchingDesignService {
    private static final int MAX_DESIGNS = 12;
    private static final String EMPTY_LEGACY_LAYER = "[]";
    private static final TypeReference<List<String>> COLOR_LIST = new TypeReference<>() { };

    private final EtchingDesignMapper etchingDesignMapper;
    private final AppUserMapper appUserMapper;
    private final AchievementMapper achievementMapper;
    private final ObjectMapper objectMapper;

    public EtchingDesignServiceImpl(EtchingDesignMapper etchingDesignMapper, AppUserMapper appUserMapper,
                                    AchievementMapper achievementMapper, ObjectMapper objectMapper) {
        this.etchingDesignMapper = etchingDesignMapper;
        this.appUserMapper = appUserMapper;
        this.achievementMapper = achievementMapper;
        this.objectMapper = objectMapper;
    }

    @Override @Transactional(readOnly = true)
    public List<EtchingDesignVO> listMine(String username) {
        return etchingDesignMapper.findByUsername(username).stream().map(this::toVO).toList();
    }

    @Override @Transactional(readOnly = true)
    public EtchingDesignVO getSelected(Long userId) {
        var design = etchingDesignMapper.findSelectedByUserId(userId);
        return design == null ? null : toVO(design);
    }

    @Override @Transactional
    public EtchingDesignVO create(String username, EtchingDesignDTO request) {
        var user = appUserMapper.findByUsername(username);
        if (user == null || !user.isActive()) throw new IllegalArgumentException("当前用户不存在或已停用");
        if (etchingDesignMapper.countByUsername(username) >= MAX_DESIGNS) {
            throw new IllegalArgumentException("每位用户最多保存12枚自制蚀刻章");
        }
        ensurePainted(request);
        var design = new EtchingDesign(user.getId(), request.name().trim(), json(request.layerOne()), EMPTY_LEGACY_LAYER);
        if (etchingDesignMapper.insert(design) != 1) throw new IllegalStateException("蚀刻章保存失败");
        return toVO(design);
    }

    @Override @Transactional
    public EtchingDesignVO update(String username, Long id, EtchingDesignDTO request) {
        ensurePainted(request);
        if (etchingDesignMapper.updateOwned(id, username, request.name().trim(), json(request.layerOne())) != 1) {
            throw new IllegalArgumentException("蚀刻章不存在或不属于当前用户");
        }
        return toVO(requiredOwned(id, username));
    }

    @Override @Transactional
    public void delete(String username, Long id) {
        if (etchingDesignMapper.deleteOwned(id, username) != 1) throw new IllegalArgumentException("蚀刻章不存在或不属于当前用户");
    }

    @Override @Transactional
    public EtchingDesignVO select(String username, Long id) {
        etchingDesignMapper.clearSelection(username);
        achievementMapper.clearSelection(username);
        if (etchingDesignMapper.selectOwned(id, username) != 1) throw new IllegalArgumentException("只能展示自己创建的蚀刻章");
        return toVO(requiredOwned(id, username));
    }

    private EtchingDesign requiredOwned(Long id, String username) {
        var design = etchingDesignMapper.findOwnedById(id, username);
        if (design == null) throw new IllegalArgumentException("蚀刻章不存在或不属于当前用户");
        return design;
    }

    private void ensurePainted(EtchingDesignDTO request) {
        if (request.layerOne().stream().allMatch(String::isBlank)) {
            throw new IllegalArgumentException("请至少为一个六角章格上色");
        }
    }

    private String json(List<String> colors) {
        try { return objectMapper.writeValueAsString(colors); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("蚀刻章颜色数据无效", exception); }
    }

    private EtchingDesignVO toVO(EtchingDesign design) {
        try {
            return new EtchingDesignVO(design.getId(), design.getName(),
                    objectMapper.readValue(design.getLayerOneJson(), COLOR_LIST),
                    design.isSelected(), design.getCreatedAt(), design.getUpdatedAt());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("蚀刻章颜色数据损坏", exception);
        }
    }
}
