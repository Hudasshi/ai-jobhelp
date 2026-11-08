package com.qznu.jobhelper.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 *
 * <p>邮箱允许省略或传空白；空白会在紧凑构造器中先归一化成 null，
 * 以免 @Email 把空格误判为非法邮箱。</p>
 */
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_]{4,20}$",
                message = "用户名必须为4~20位字母、数字或下划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度必须为6~32位")
        String password,

        @Size(max = 20, message = "昵称不能超过20个字符")
        String nickname,

        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱不能超过100个字符")
        String email
) {
    public RegisterRequest {
        if (email != null && email.isBlank()) {
            email = null;
        }
        if (nickname != null && nickname.isBlank()) {
            nickname = null;
        }
    }
}
