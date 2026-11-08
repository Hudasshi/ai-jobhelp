package com.qznu.jobhelper.common.exception;

import lombok.Getter;

/**
 * 可预期的业务异常。
 *
 * <p>业务异常使用 HTTP 200 + JSON code 表达，目的是让前端统一通过响应体处理
 * “参数错误、冲突、配额耗尽”等业务分支；HTTP 401/403/500 只用于认证、授权和系统故障。</p>
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    private BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException badRequest(String message) {
        return new BizException(400, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(401, message);
    }

    public static BizException notFound(String message) {
        return new BizException(404, message);
    }

    public static BizException conflict(String message) {
        return new BizException(409, message);
    }

    public static BizException quotaExceeded(String message) {
        return new BizException(422, message);
    }

    public static BizException tooManyRequests(String message) {
        return new BizException(429, message);
    }

    public static BizException business(String message) {
        return new BizException(1, message);
    }
}
