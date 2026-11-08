package com.qznu.jobhelper.admin.controller;

import com.qznu.jobhelper.admin.dto.DevAiTestRequest;
import com.qznu.jobhelper.common.result.Result;
import com.qznu.jobhelper.common.web.UserContext;
import com.qznu.jobhelper.task.AiGateway;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * 仅在 dev Profile 下装载的 AI 连通性测试接口。
 *
 * <p>生产环境不会注册该 Controller，避免暴露可直接消耗模型额度的调试入口。</p>
 */
@Profile("dev")
@RestController
@RequestMapping("/api/dev")
public class DevAiController {

    private final AiGateway aiGateway;

    public DevAiController(AiGateway aiGateway) {
        this.aiGateway = aiGateway;
    }

    @PostMapping("/ai-test")
    public Result<Map<String, Object>> test(
            @Valid @RequestBody DevAiTestRequest request) {
        String text = aiGateway.chat(
                UserContext.requireUserId(),
                "dev_test",
                request.prompt(),
                "dev_test:" + UUID.randomUUID()).text();
        return Result.ok(Map.of("responseText", text));
    }
}
