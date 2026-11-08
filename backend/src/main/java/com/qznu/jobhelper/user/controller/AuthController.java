package com.qznu.jobhelper.user.controller;

import com.qznu.jobhelper.common.result.Result;
import com.qznu.jobhelper.common.web.RequestUtils;
import com.qznu.jobhelper.user.dto.LoginRequest;
import com.qznu.jobhelper.user.dto.RegisterRequest;
import com.qznu.jobhelper.user.service.AuthService;
import com.qznu.jobhelper.user.vo.AuthData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 注册与登录接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<AuthData> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request));
    }

    @PostMapping("/login")
    public Result<AuthData> login(@Valid @RequestBody LoginRequest request,
                                  HttpServletRequest servletRequest) {
        return Result.ok(authService.login(request, RequestUtils.clientIp(servletRequest)));
    }
}
