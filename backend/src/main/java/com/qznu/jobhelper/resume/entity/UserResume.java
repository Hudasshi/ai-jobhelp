package com.qznu.jobhelper.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 简历版本。
 *
 * <p>contentJson 是精确的结构化快照，contentMd 仅用于展示与导出。
 * 诊断和打印都以快照为准，能力库后续变化不会回写历史版本。</p>
 */
@Getter
@Setter
@TableName("user_resume")
public class UserResume {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String sourceType;
    private String status;
    private String targetPosition;
    private String contentJson;
    private String contentMd;
    private String filePath;
    private String rawText;
    private Integer syncStatus;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
