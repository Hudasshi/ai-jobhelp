package com.qznu.jobhelper.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 单个系统配置项。 */
public record ConfigItem(
        @NotBlank(message = "配置键不能为空")
        String configKey,

        @NotBlank(message = "配置值不能为空")
        @Size(max = 500, message = "配置值不能超过500字")
        String configValue
) {
}
