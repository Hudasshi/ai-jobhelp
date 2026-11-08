package com.qznu.jobhelper.ability.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 能力条目新增/编辑请求。
 *
 * <p>detail 使用 Map 保留六类条目的差异化字段；具体键由前端 field-spec 驱动。</p>
 */
public record AbilityRequest(
        Long id,
        @NotBlank(message = "条目类型不能为空") String entryType,
        @NotBlank(message = "标题不能为空")
        @Size(max = 500, message = "标题不能超过500个字符") String title,
        @NotNull(message = "明细不能为空") Map<String, Object> detail,
        Integer sortOrder
) {
}
