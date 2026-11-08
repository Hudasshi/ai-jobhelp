package com.qznu.jobhelper.common.exception;

import lombok.Getter;

/**
 * 已登录但账号失效或权限不足。
 */
@Getter
public class ForbiddenException extends RuntimeException {

    private final String reason;

    private ForbiddenException(String message, String reason) {
        super(message);
        this.reason = reason;
    }

    public static ForbiddenException accountInvalid() {
        return new ForbiddenException("账号已被禁用或注销", "ACCOUNT_INVALID");
    }

    public static ForbiddenException forbidden() {
        return new ForbiddenException("无权访问该资源", "FORBIDDEN");
    }
}
