package com.qznu.jobhelper.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 管理员覆盖目标用户每日配额；值是覆盖值，不是增量。 */
public record QuotaOverrideRequest(
        @NotNull(message = "配额不能为空")
        @Min(value = 0, message = "配额不能小于0")
        @Max(value = 999, message = "配额不能大于999")
        Integer override
) {
}
