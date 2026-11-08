package com.qznu.jobhelper.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 同步解析 JD 的请求体。 */
public record ParseJobDescriptionRequest(
        @NotBlank(message = "JD不能为空")
        @Size(max = 8000, message = "JD不能超过8000字")
        String jdRaw
) {
}
