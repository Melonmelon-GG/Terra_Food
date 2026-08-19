package com.dayan.food.controller;

import com.dayan.food.entity.dto.RegionResolveDTO;
import com.dayan.food.entity.vo.RegionVO;
import com.dayan.food.service.RegionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionService regionService;

    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }

    @GetMapping
    public List<RegionVO> list() {
        return regionService.list();
    }

    @PostMapping("/resolve")
    public RegionVO resolve(@Valid @RequestBody RegionResolveDTO request) {
        return regionService.resolveLocation(request.province(), request.city());
    }
}
