package com.qznu.jobhelper.user.service;

import com.qznu.jobhelper.common.exception.BizException;
import com.qznu.jobhelper.common.exception.ForbiddenException;
import com.qznu.jobhelper.common.web.JwtUtil;
import com.qznu.jobhelper.support.log.SystemLogService;
import com.qznu.jobhelper.user.converter.UserConverter;
import com.qznu.jobhelper.user.dto.ChangePasswordRequest;
import com.qznu.jobhelper.user.dto.LoginRequest;
import com.qznu.jobhelper.user.dto.RegisterRequest;
import com.qznu.jobhelper.user.entity.User;
import com.qznu.jobhelper.user.mapper.UserMapper;
import com.qznu.jobhelper.user.vo.AuthData;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 认证与账号服务。
 */
@Service
public class AuthService {

    /**
     * 固定 BCrypt 串，用于“用户不存在”时仍执行一次密码校验，降低用户名枚举风险。
     */
    private static final String DUMMY_HASH =
            "$2b$10$H5mXN4rDSkUBdxQZejoF8uABP1T3uBdKunVPEi3u5f8id6jS0iGMa";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserConverter userConverter;
    private final SystemLogService systemLogService;
    private final AccountPurgeService accountPurgeService;
    private final Clock clock;

    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       UserConverter userConverter,
                       SystemLogService systemLogService,
                       AccountPurgeService accountPurgeService,
                       Clock clock) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.userConverter = userConverter;
        this.systemLogService = systemLogService;
        this.accountPurgeService = accountPurgeService;
        this.clock = clock;
    }

    /**
     * 注册即登录：用户与 token 在同一次业务事务中完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthData register(RegisterRequest request) {
        if (userMapper.countByUsernameIncludingDeleted(request.username()) > 0) {
            throw BizException.conflict("用户名已被注册");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null ? request.username() : request.nickname());
        user.setEmail(request.email());
        user.setRole("user");
        user.setStatus(1);
        user.setQuotaGeneration(0L);
        user.setDeleted(0);
        user.setLastLoginAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        try {
            if (userMapper.insert(user) != 1) {
                throw new IllegalStateException("注册用户写入失败");
            }
        } catch (DuplicateKeyException exception) {
            throw BizException.conflict("用户名已被注册");
        }

        String token = jwtUtil.generate(user.getId(), user.getRole());
        return userConverter.toAuthData(user, token);
    }

    /**
     * 登录成功与失败均写审计日志；不存在用户和错误密码统一返回相同文案。
     */
    public AuthData login(LoginRequest request, String ip) {
        User user = userMapper.selectByUsernameIncludingDeleted(request.username());

        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            systemLogService.write(null, request.username(), "login_fail",
                    false, ip, "用户名不存在");
            throw BizException.business("用户名或密码错误");
        }

        if (!Integer.valueOf(1).equals(user.getStatus())
                || !Integer.valueOf(0).equals(user.getDeleted())) {
            systemLogService.write(user.getId(), user.getUsername(), "login_fail",
                    false, ip, "账号已禁用或注销");
            throw ForbiddenException.accountInvalid();
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            systemLogService.write(user.getId(), user.getUsername(), "login_fail",
                    false, ip, "密码错误");
            throw BizException.business("用户名或密码错误");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (userMapper.updateLastLoginAt(user.getId(), now) != 1) {
            throw ForbiddenException.accountInvalid();
        }

        String token;
        try {
            token = jwtUtil.generate(user.getId(), user.getRole());
        } catch (Exception exception) {
            systemLogService.write(user.getId(), user.getUsername(), "login_fail",
                    false, ip, "TOKEN_ERROR");
            throw new IllegalStateException("签发登录令牌失败", exception);
        }

        user.setLastLoginAt(now);
        systemLogService.write(user.getId(), user.getUsername(), "login",
                true, ip, "用户登录成功");
        return userConverter.toAuthData(user, token);
    }

    public Map<String, Object> profile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw ForbiddenException.accountInvalid();
        }
        return userConverter.toProfile(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw ForbiddenException.accountInvalid();
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw BizException.business("原密码不正确");
        }
        String encoded = passwordEncoder.encode(request.newPassword());
        if (userMapper.updatePassword(userId, encoded, LocalDateTime.now(clock)) != 1) {
            throw ForbiddenException.accountInvalid();
        }
    }

    public Map<String, Object> deleteOwnData(Long userId, String username, String ip) {
        return accountPurgeService.purge(userId, username, ip);
    }
}
