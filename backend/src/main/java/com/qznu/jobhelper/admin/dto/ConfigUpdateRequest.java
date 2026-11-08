package com.qznu.jobhelper.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 批量更新配置；13 个键允许提交任意非空子集，但不允许重复键。 */
public record ConfigUpdateRequest(
        @NotEmpty(message = "配置项不能为空")
        @Size(max = 13, message = "配置项不能超过13个")
        List<@Valid ConfigItem> items
) {
}
