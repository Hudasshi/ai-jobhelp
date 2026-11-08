package com.qznu.jobhelper.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Web 请求辅助方法。
 */
public final class RequestUtils {

    private RequestUtils() {
    }

    /**
     * 优先使用代理链首段地址，退化为 remoteAddr。
     * 该值仅用于审计，不作为安全授权依据。
     */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 从 Authorization: Bearer xxx 中提取令牌。
     */
    public static String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
