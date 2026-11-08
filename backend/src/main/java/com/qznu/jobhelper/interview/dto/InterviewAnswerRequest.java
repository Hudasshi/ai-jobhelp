package com.qznu.jobhelper.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 提交单题答案。
 *
 * <p>requestId 是面试内的幂等键：网络重试必须复用同一个 UUID，否则服务端会认为
 * 用户又提交了一次新点评并再次扣配额。</p>
 */
public record InterviewAnswerRequest(
        @NotNull(message = "题号不能为空")
        @Min(value = 0, message = "题号不能小于0")
        @Max(value = 5, message = "题号不能大于5")
        Integer index,

        @NotNull(message = "答案不能为空")
        @Size(max = 2000, message = "答案不能超过2000字")
        String answer,

        @NotNull(message = "requestId不能为空")
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
                message = "requestId必须为UUID"
        )
        String requestId
) {
}
