package com.qznu.jobhelper.diagnosis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发起简历诊断请求。
 *
 * <p>JD 长度限制是接口契约的一部分：太短无法做有效匹配，太长会让单次 AI 上下文
 * 不可控。deliveryId 是可选的关联入口，不用于决定数据归属。</p>
 */
public record CreateDiagnosisRequest(
        @NotNull(message = "简历版本不能为空")
        Long resumeId,

        @NotBlank(message = "JD不能为空")
        @Size(min = 50, max = 8000, message = "JD长度必须为50至8000字")
        String jd,

        Long deliveryId
) {
}
