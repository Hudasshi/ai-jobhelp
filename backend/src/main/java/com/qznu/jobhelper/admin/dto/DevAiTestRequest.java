package com.qznu.jobhelper.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 开发环境 AI 连通性测试，禁止输入真实个人正文或密钥。 */
public record DevAiTestRequest(
        @NotBlank(message = "测试提示词不能为空")
        @Size(max = 2000, message = "测试提示词不能超过2000字")
        String prompt
) {
}
