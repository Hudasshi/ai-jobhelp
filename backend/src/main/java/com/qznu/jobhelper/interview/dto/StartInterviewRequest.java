package com.qznu.jobhelper.interview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 启动模拟面试请求。
 *
 * @param deliveryId 必填，且必须是当前用户自己的投递
 * @param focus      出题侧重，省略时按综合面试处理
 */
public record StartInterviewRequest(
        @NotNull(message = "投递记录不能为空")
        Long deliveryId,

        @Pattern(
                regexp = "^(technical_deep_dive|comprehensive)?$",
                message = "面试侧重点不合法"
        )
        String focus
) {
}
