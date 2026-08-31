package com.dayan.food.entity.po;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class EtchingDesign {
    private Long id;
    private Long userId;
    private String name;
    private String layerOneJson;
    private String layerTwoJson;
    private boolean selected;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public EtchingDesign(Long userId, String name, String layerOneJson, String layerTwoJson) {
        this.userId = userId;
        this.name = name;
        this.layerOneJson = layerOneJson;
        this.layerTwoJson = layerTwoJson;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }
}
