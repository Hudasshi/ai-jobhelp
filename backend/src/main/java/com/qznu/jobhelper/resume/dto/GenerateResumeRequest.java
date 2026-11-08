package com.qznu.jobhelper.resume.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 异步生成简历草稿请求。
 */
public record GenerateResumeRequest(
        @NotEmpty(message = "至少选择一个能力条目") List<Long> entryIds,
        Long jobTargetId,
        @Size(max = 8000, message = "JD不能超过8000字") String jdText
) {
}
