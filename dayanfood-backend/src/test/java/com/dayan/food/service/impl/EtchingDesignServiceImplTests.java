package com.dayan.food.service.impl;

import com.dayan.food.entity.dto.EtchingDesignDTO;
import com.dayan.food.entity.enums.UserRole;
import com.dayan.food.entity.po.AppUser;
import com.dayan.food.entity.po.EtchingDesign;
import com.dayan.food.mapper.AchievementMapper;
import com.dayan.food.mapper.AppUserMapper;
import com.dayan.food.mapper.EtchingDesignMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EtchingDesignServiceImplTests {
    @Mock private EtchingDesignMapper etchingDesignMapper;
    @Mock private AppUserMapper appUserMapper;
    @Mock private AchievementMapper achievementMapper;
    private EtchingDesignServiceImpl service;

    @BeforeEach void setUp() {
        service = new EtchingDesignServiceImpl(etchingDesignMapper, appUserMapper, achievementMapper, new ObjectMapper());
    }

    @Test void createPersistsOneHundredSixtyNineCellCanvas() {
        AppUser user = new AppUser("reader", "encoded", "食客", UserRole.USER);
        ReflectionTestUtils.setField(user, "id", 7L);
        when(appUserMapper.findByUsername("reader")).thenReturn(user);
        when(etchingDesignMapper.insert(any(EtchingDesign.class))).thenReturn(1);

        var result = service.create("reader", paintedRequest());

        assertEquals(169, result.layerOne().size());
        assertEquals("#9A352E", result.layerOne().getFirst());
    }

    @Test void createRejectsCompletelyEmptyCanvas() {
        AppUser user = new AppUser("reader", "encoded", "食客", UserRole.USER);
        when(appUserMapper.findByUsername("reader")).thenReturn(user);
        var empty = new ArrayList<String>();
        for (int index = 0; index < 169; index++) empty.add("");

        assertThrows(IllegalArgumentException.class,
                () -> service.create("reader", new EtchingDesignDTO("空章", empty)));
        verify(etchingDesignMapper, never()).insert(any());
    }

    @Test void selectingUserDesignClearsSystemAchievementSelection() {
        EtchingDesign design = new EtchingDesign(7L, "双层章", jsonLayer(), jsonEmptyLayer());
        ReflectionTestUtils.setField(design, "id", 3L);
        ReflectionTestUtils.setField(design, "selected", true);
        when(etchingDesignMapper.selectOwned(3L, "reader")).thenReturn(1);
        when(etchingDesignMapper.findOwnedById(3L, "reader")).thenReturn(design);

        service.select("reader", 3L);

        verify(etchingDesignMapper).clearSelection("reader");
        verify(achievementMapper).clearSelection("reader");
    }

    private EtchingDesignDTO paintedRequest() {
        var one = new ArrayList<String>();
        for (int index = 0; index < 169; index++) one.add(index == 0 ? "#9A352E" : "");
        return new EtchingDesignDTO("单层章", one);
    }
    private String jsonLayer() { return "[\"#9A352E\"" + ",\"\"".repeat(168) + "]"; }
    private String jsonEmptyLayer() { return "[\"\"" + ",\"\"".repeat(168) + "]"; }
}
