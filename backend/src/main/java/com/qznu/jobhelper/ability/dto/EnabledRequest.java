package com.qznu.jobhelper.ability.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 能力条目启停请求。
 */
public record EnabledRequest(
        @NotNull(message = "enabled不能为空")
        @Min(value = 0, message = "enabled只能为0或1")
        @Max(value = 1, message = "enabled只能为0或1")
        Integer enabled
) {
}
