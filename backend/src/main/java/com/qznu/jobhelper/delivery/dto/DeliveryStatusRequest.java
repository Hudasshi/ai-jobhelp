package com.qznu.jobhelper.delivery.dto;

import jakarta.validation.constraints.NotBlank;

/** 投递状态流转请求。 */
public record DeliveryStatusRequest(
        @NotBlank(message = "目标状态不能为空")
        String status
) {
}
