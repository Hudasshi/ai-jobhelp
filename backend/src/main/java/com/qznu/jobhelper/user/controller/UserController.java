package com.qznu.jobhelper.user.controller;

import com.qznu.jobhelper.common.result.Result;
import com.qznu.jobhelper.common.web.RequestUtils;
import com.qznu.jobhelper.common.web.UserContext;
import com.qznu.jobhelper.user.dto.ChangePasswordRequest;
import com.qznu.jobhelper.user.dto.DeleteOwnDataRequest;
import com.qznu.jobhelper.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 个人资料、改密和注销接口。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        return Result.ok(authService.profile(UserContext.requireUserId()));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(UserContext.requireUserId(), request);
        return Result.ok();
    }

    @DeleteMapping("/data")
    public Result<Map<String, Object>> deleteOwnData(
            @Valid @RequestBody DeleteOwnDataRequest request,
            HttpServletRequest servletRequest) {
        return Result.ok(authService.deleteOwnData(
                UserContext.requireUserId(),
                request.username(),
                RequestUtils.clientIp(servletRequest)
        ));
    }
}
