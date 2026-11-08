package com.qznu.jobhelper.resume.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 保存简历版本请求。
 *
 * <p>sourceType 决定附带的来源字段：generate 使用 draftTaskId，
 * upload 使用 entryIds/sourceResumeId，diagnose 使用 diagnoseId。</p>
 */
public record ResumeSaveRequest(
        @NotBlank(message = "版本名称不能为空")
        @Size(max = 100, message = "版本名称不能超过100个字符") String name,
        @Size(max = 100, message = "目标岗位不能超过100个字符") String targetPosition,
        @NotBlank(message = "来源类型不能为空") String sourceType,
        List<Long> entryIds,
        Long sourceResumeId,
        Long draftTaskId,
        Long diagnoseId
) {
}
