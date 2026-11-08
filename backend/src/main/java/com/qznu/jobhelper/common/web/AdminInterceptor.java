package com.qznu.jobhelper.common.web;

import com.qznu.jobhelper.common.exception.AuthException;
import com.qznu.jobhelper.common.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理端角色拦截器。
 *
 * <p>只读取 JwtInterceptor 已从数据库写入 UserContext 的 role，
 * 不使用 JWT 载荷里的 role，防止旧令牌继续保留管理员权限。</p>
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        UserContextData data = UserContext.get();
        if (data == null) {
            throw new AuthException();
        }
        if (!"admin".equals(data.role())) {
            throw ForbiddenException.forbidden();
        }
        return true;
    }
}
