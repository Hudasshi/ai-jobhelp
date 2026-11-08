package com.qznu.jobhelper.admin.controller;

import com.qznu.jobhelper.admin.dto.ConfigUpdateRequest;
import com.qznu.jobhelper.admin.dto.QuotaOverrideRequest;
import com.qznu.jobhelper.admin.dto.UserStatusRequest;
import com.qznu.jobhelper.admin.service.AdminService;
import com.qznu.jobhelper.common.result.Result;
import com.qznu.jobhelper.common.web.RequestUtils;
import com.qznu.jobhelper.common.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 管理端接口。
 *
 * <p>AdminInterceptor 已拦截整个 /api/admin/** 路径；这里不重复解析角色，
 * 但写审计时仍使用当前 token 的 userId，保证操作者不可伪造。</p>
 */
@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(
            @RequestParam(defaultValue = "7") int days) {
        return Result.ok(adminService.stats(days));
    }

    @GetMapping("/users")
    public Result<Map<String, Object>> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.ok(adminService.pageUsers(keyword, status, startDate, endDate, page, size));
    }

    @GetMapping("/users/{id}")
    public Result<Map<String, Object>> user(@PathVariable Long id) {
        return Result.ok(adminService.userDetail(id));
    }

    @PutMapping("/users/{id}/quota")
    public Result<Map<String, Object>> quota(@PathVariable Long id,
                                             @Valid @RequestBody QuotaOverrideRequest request,
                                             HttpServletRequest servletRequest) {
        return Result.ok(adminService.adjustQuota(
                UserContext.requireUserId(), id, request,
                RequestUtils.clientIp(servletRequest)));
    }

    @PutMapping("/users/{id}/status")
    public Result<Map<String, Object>> status(@PathVariable Long id,
                                              @Valid @RequestBody UserStatusRequest request,
                                              HttpServletRequest servletRequest) {
        return Result.ok(adminService.changeUserStatus(
                UserContext.requireUserId(), id, request,
                RequestUtils.clientIp(servletRequest)));
    }

    @GetMapping("/diagnoses")
    public Result<Map<String, Object>> diagnoses(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.ok(adminService.pageDiagnoses(
                userId, status, startDate, endDate, page, size));
    }

    @GetMapping("/ai-logs")
    public Result<Map<String, Object>> aiLogs(
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.ok(adminService.pageAiLogs(
                scene, status, startDate, endDate, page, size));
    }

    @GetMapping("/logs")
    public Result<Map<String, Object>> logs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer result,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size) {
        return Result.ok(adminService.pageSystemLogs(
                action, result, startDate, endDate, page, size));
    }

    @GetMapping("/configs")
    public Result<Map<String, Object>> configs() {
        return Result.ok(adminService.configs());
    }

    @PutMapping("/configs")
    public Result<Map<String, Object>> configs(
            @Valid @RequestBody ConfigUpdateRequest request) {
        return Result.ok(adminService.updateConfigs(request));
    }
}
