package com.qznu.jobhelper.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 管理员启用或禁用账号。 */
public record UserStatusRequest(
        @NotNull(message = "账号状态不能为空")
        @Min(value = 0, message = "账号状态只能为0或1")
        @Max(value = 1, message = "账号状态只能为0或1")
        Integer status
) {
}
