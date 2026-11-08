package com.qznu.jobhelper.common.web;

import com.qznu.jobhelper.common.exception.AuthException;

/**
 * ThreadLocal 登录上下文。
 *
 * <p>必须由过滤器 finally 兜底清理，避免线程池复用线程时把上一个用户身份带给下一个请求。</p>
 */
public final class UserContext {

    private static final ThreadLocal<UserContextData> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(UserContextData data) {
        HOLDER.set(data);
    }

    public static UserContextData get() {
        return HOLDER.get();
    }

    public static Long requireUserId() {
        UserContextData data = HOLDER.get();
        if (data == null) {
            throw new AuthException();
        }
        return data.userId();
    }

    public static String requireRole() {
        UserContextData data = HOLDER.get();
        if (data == null) {
            throw new AuthException();
        }
        return data.role();
    }

    public static String ip() {
        UserContextData data = HOLDER.get();
        return data == null ? null : data.ip();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
