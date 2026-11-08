package com.qznu.jobhelper.user.controller;

import com.qznu.jobhelper.common.result.Result;
import com.qznu.jobhelper.common.web.UserContext;
import com.qznu.jobhelper.user.service.DashboardService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工作台摘要和异步任务通知。
 */
@Validated
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.ok(dashboardService.summary(UserContext.requireUserId()));
    }

    @GetMapping("/notices")
    public Result<List<Map<String, Object>>> notices(
            @RequestParam(defaultValue = "5") @Min(1) @Max(5) int limit) {
        return Result.ok(dashboardService.notices(UserContext.requireUserId(), limit));
    }
}
