package com.qznu.jobhelper.diagnosis.dto;

import jakarta.validation.constraints.NotNull;

/** 切换某条诊断建议的采纳状态。 */
public record AdoptRequest(
        @NotNull(message = "采纳状态不能为空")
        Boolean adopted
) {
}
