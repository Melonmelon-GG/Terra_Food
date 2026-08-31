package com.dayan.food.entity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EtchingDesignDTO(
        @NotBlank(message = "蚀刻章名称不能为空")
        @Size(max = 50, message = "蚀刻章名称不能超过50个字符")
        String name,

        @Valid
        @NotNull(message = "画布不能为空")
        @Size(min = 169, max = 169, message = "画布必须包含169个六角章格")
        List<@Pattern(regexp = "^(?:|#[0-9A-Fa-f]{6})$", message = "章格颜色必须为空或六位十六进制颜色") String> layerOne
) {
}
