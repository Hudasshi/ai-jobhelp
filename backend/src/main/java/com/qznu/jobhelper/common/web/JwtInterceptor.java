package com.qznu.jobhelper.common.web;

import com.qznu.jobhelper.common.exception.AuthException;
import com.qznu.jobhelper.common.exception.ForbiddenException;
import com.qznu.jobhelper.user.entity.User;
import com.qznu.jobhelper.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * 用户认证拦截器。
 *
 * <p>每次受保护请求都查一次 user 表，确保禁用/注销立即生效。
 * 白名单使用 method + path 精确匹配，不允许把 /api/auth/** 整体放行。</p>
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final Set<String> ANONYMOUS_KEYS = Set.of(
            "POST /api/auth/register",
            "POST /api/auth/login",
            "GET /ping"
    );

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public JwtInterceptor(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String key = request.getMethod() + " " + request.getRequestURI();
        if (ANONYMOUS_KEYS.contains(key)) {
            return true;
        }

        String token = RequestUtils.bearerToken(request);
        if (token == null) {
            throw new AuthException();
        }

        JwtPrincipal principal = jwtUtil.parse(token);
        User user = userMapper.selectById(principal.userId());
        if (user == null || !Integer.valueOf(0).equals(user.getDeleted())) {
            throw ForbiddenException.accountInvalid();
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw ForbiddenException.accountInvalid();
        }

        UserContext.set(new UserContextData(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRole(),
                user.getStatus(),
                RequestUtils.clientIp(request)
        ));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception exception) {
        UserContext.clear();
    }
}
