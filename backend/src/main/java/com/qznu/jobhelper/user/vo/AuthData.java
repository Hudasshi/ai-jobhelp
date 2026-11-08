package com.qznu.jobhelper.user.vo;

/**
 * 注册、登录共用的认证响应。
 */
public record AuthData(String token, String nickname, String role) {
}
