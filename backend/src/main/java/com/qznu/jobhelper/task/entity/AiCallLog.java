package com.qznu.jobhelper.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 调用元数据日志。
 *
 * <p>严禁写入 Prompt、回答正文、文件内容或密钥，管理端只展示统计字段。</p>
 */
@Getter
@Setter
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String scene;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer durationMs;
    private String status;
    private Integer degraded;
    private BigDecimal costEstimate;
    private String operationId;
    private LocalDate quotaDate;
    private Long quotaGeneration;
    private String quotaState;
    private LocalDateTime createdAt;
}
