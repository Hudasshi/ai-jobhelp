package com.qznu.jobhelper.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 注销请求，要求再次输入当前用户名。
 */
public record DeleteOwnDataRequest(
        @NotBlank(message = "用户名不能为空") String username
) {
}
