package com.qznu.jobhelper.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI 异步任务，同时兼具站内通知能力。
 */
@Getter
@Setter
@TableName("ai_task")
public class AiTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String taskType;
    private Long bizId;
    private String status;
    private Integer progress;
    private String failReason;
    private Long resultRef;
    private String resultJson;
    private String inputSnapshot;
    private LocalDate quotaDate;
    private Long quotaGeneration;
    private String quotaState;
    private Integer refunded;
    private Integer readFlag;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime timeoutAt;
}
