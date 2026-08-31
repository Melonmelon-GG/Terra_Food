package com.dayan.food.service;

import com.dayan.food.entity.dto.EtchingDesignDTO;
import com.dayan.food.entity.vo.EtchingDesignVO;

import java.util.List;

public interface EtchingDesignService {
    List<EtchingDesignVO> listMine(String username);
    EtchingDesignVO getSelected(Long userId);
    EtchingDesignVO create(String username, EtchingDesignDTO request);
    EtchingDesignVO update(String username, Long id, EtchingDesignDTO request);
    void delete(String username, Long id);
    EtchingDesignVO select(String username, Long id);
}
