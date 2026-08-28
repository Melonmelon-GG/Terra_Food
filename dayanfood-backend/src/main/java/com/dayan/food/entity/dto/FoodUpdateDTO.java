package com.dayan.food.entity.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FoodUpdateDTO(
        @NotBlank @Size(max = 100) String name,
        @NotNull Long regionId,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @Size(max = 500) String address,
        @NotBlank @Size(max = 1000) String summary,
        @NotBlank @Size(max = 10000) String story,
        @NotBlank @Size(max = 500) String ingredients,
        @Size(max = 500)
        @Pattern(
                regexp = "^(?:$|https?://(?!.*\\.\\.)[^\\s\\\\]+|/uploads/(?!.*\\.\\.)[0-9A-Za-z._-]+\\.(?:jpg|jpeg|png|webp))$",
                message = "图片地址必须为空、HTTP(S) 地址或站内 /uploads 上传地址（禁止路径穿越与查询串）"
        )
        String imageUrl,
        @Size(max = 1000) String remark
) {
}
