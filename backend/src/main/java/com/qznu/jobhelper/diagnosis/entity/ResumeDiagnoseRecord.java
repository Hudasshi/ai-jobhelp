package com.qznu.jobhelper.diagnosis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 简历诊断记录。
 *
 * <p>即使用户之后删除简历或注销账号，诊断元数据仍会保留；
 * 正文在账号注销或保留期结束后清空，防止隐私长期滞留。</p>
 */
@Getter
@Setter
@TableName("resume_diagnose_record")
public class ResumeDiagnoseRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String usernameSnapshot;
    private String positionSnapshot;
    private Long resumeId;
    private Long jobTargetId;
    private Long deliveryId;
    private Long taskId;
    private Integer score;
    private String reportJson;
    private String status;
    private String failReason;
    private Integer costSeconds;
    private Integer tokens;
    private LocalDateTime createdAt;
}
