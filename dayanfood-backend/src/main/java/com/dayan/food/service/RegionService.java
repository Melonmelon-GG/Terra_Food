package com.dayan.food.service;

import com.dayan.food.entity.vo.RegionVO;
import com.dayan.food.repository.RegionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RegionService {
    private final RegionRepository repository;

    public RegionService(RegionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RegionVO> list() {
        return repository.findAll().stream()
                .map(RegionVO::from)
                .toList();
    }
}
