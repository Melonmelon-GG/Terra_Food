package com.dayan.food.entity.po;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Region {

    private Long id;

    private String name;

    private String province;

    private String description;

    public Region(String name, String province, String description) {
        this.name = name;
        this.province = province;
        this.description = description;
    }
}
