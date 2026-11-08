package com.qznu.jobhelper.task;

import com.qznu.jobhelper.support.config.ConfigService;
import com.qznu.jobhelper.support.quota.QuotaService;
import com.qznu.jobhelper.task.entity.AiCallLog;
import com.qznu.jobhelper.task.mapper.AiCallLogMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 统一 AI 门面。
 *
 * <p>有 API Key 时通过 LangChain4j 调用 OpenAI 兼容接口；没有 Key 或显式 mock 时，
 * 使用本地演示响应，保证项目离线也能完整验收。每次调用都写元数据日志，不写正文。</p>
 */
@Slf4j
@Service
public class AiGateway {

    private final AiCallLogMapper aiCallLogMapper;
    private final ConfigService configService;
    private final QuotaService quotaService;
    private final Clock clock;
    private final String apiKey;
    private final String baseUrl;
    private final String providerModel;
    private final String mockMode;

    public AiGateway(AiCallLogMapper aiCallLogMapper,
                     ConfigService configService,
                     QuotaService quotaService,
                     Clock clock,
                     @Value("${ai.api-key:}") String apiKey,
                     @Value("${ai.base-url}") String baseUrl,
                     @Value("${ai.model}") String providerModel,
                     @Value("${ai.mock-mode:auto}") String mockMode) {
        this.aiCallLogMapper = aiCallLogMapper;
        this.configService = configService;
        this.quotaService = quotaService;
        this.clock = clock;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.providerModel = providerModel;
        this.mockMode = mockMode;
    }

    /**
     * 生成文本。真实模型失败时降级到本地演示结果，避免课堂演示被外部网络中断。
     */
    public AiResponse chat(Long userId, String scene, String prompt, String operationId) {
        long started = System.currentTimeMillis();
        int promptTokens = estimateTokens(prompt);
        String text;
        boolean degraded = false;
        boolean success = true;

        if (shouldUseMock()) {
            text = mockText(scene);
        } else {
            try {
                String modelName = configService.getString("ai.model");
                OpenAiChatModel model = OpenAiChatModel.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .modelName(modelName)
                        .timeout(Duration.ofMillis(configService.getInt("ai.timeout-ms")))
                        .maxRetries(configService.getInt("ai.retry.max"))
                        .build();
                text = model.chat(prompt);
                if (text == null || text.isBlank()) {
                    throw new IllegalStateException("AI 返回空内容");
                }
            } catch (Exception exception) {
                log.warn("真实 AI 调用失败，降级为本地演示结果: {}", exception.getMessage());
                text = mockText(scene);
                degraded = true;
            }
        }

        int completionTokens = estimateTokens(text);
        long duration = System.currentTimeMillis() - started;
        logCall(userId, scene, promptTokens, completionTokens, (int) duration,
                success, degraded, operationId);
        return new AiResponse(text, promptTokens, completionTokens, degraded);
    }

    /**
     * 同步场景（JD 解析、逐题点评）也需要记录调用元数据。
     */
    public void logSynchronousCall(Long userId, String scene, int promptTokens,
                                   int completionTokens, long durationMs,
                                   boolean success, boolean degraded, String operationId) {
        logCall(userId, scene, promptTokens, completionTokens, (int) durationMs,
                success, degraded, operationId);
    }

    public void logTaskCall(Long userId, String scene, String operationId,
                            int promptTokens, int completionTokens,
                            long durationMs, boolean success, boolean degraded,
                            LocalDateTime quotaDate, Long generation) {
        logCall(userId, scene, promptTokens, completionTokens, (int) durationMs,
                success, degraded, operationId);
    }

    private boolean shouldUseMock() {
        return "always".equalsIgnoreCase(mockMode)
                || apiKey == null
                || apiKey.isBlank();
    }

    private String mockText(String scene) {
        return switch (scene) {
            case "resume_parse" -> "已启用本地演示解析，系统将从简历文本中提取教育与经历条目。";
            case "resume_generate" -> "已启用本地演示生成，系统将按岗位重点重排能力条目。";
            case "diagnose" -> "已启用本地演示诊断，系统将按技能、项目、教育、表达四个维度评分。";
            case "jd_parse" -> "已启用本地演示 JD 解析。";
            case "interview_gen" -> "已启用本地演示面试出题。";
            case "interview_feedback" -> "已启用本地演示逐题点评。";
            default -> "本地演示模式响应";
        };
    }

    private void logCall(Long userId, String scene, int promptTokens,
                         int completionTokens, int durationMs, boolean success,
                         boolean degraded, String operationId) {
        try {
            AiCallLog logRow = new AiCallLog();
            logRow.setUserId(userId);
            logRow.setScene(scene);
            logRow.setModel(configService.getString("ai.model"));
            logRow.setPromptTokens(promptTokens);
            logRow.setCompletionTokens(completionTokens);
            logRow.setDurationMs(Math.max(durationMs, 0));
            logRow.setStatus(success ? "success" : "fail");
            logRow.setDegraded(degraded ? 1 : 0);
            BigDecimal cost = configService.getDecimal("ai.price.input")
                    .multiply(BigDecimal.valueOf(promptTokens))
                    .add(configService.getDecimal("ai.price.output")
                            .multiply(BigDecimal.valueOf(completionTokens)));
            logRow.setCostEstimate(cost);
            logRow.setOperationId(operationId);
            logRow.setQuotaDate(quotaService.today());
            logRow.setQuotaState("charged");
            logRow.setCreatedAt(LocalDateTime.now(clock));
            aiCallLogMapper.insert(logRow);
        } catch (Exception exception) {
            log.error("写 AI 调用日志失败", exception);
        }
    }

    private int estimateTokens(String text) {
        if (text == null) {
            return 0;
        }
        // 中文场景用字符数近似 token，日志只用于趋势展示，不参与计费。
        return Math.max(1, text.length() / 3);
    }

    public String configuredModel() {
        String dbModel = configService.getString("ai.model");
        return dbModel == null || dbModel.isBlank() ? providerModel : dbModel;
    }

    public record AiResponse(String text, int promptTokens,
                             int completionTokens, boolean degraded) {
    }
}
