package com.qznu.jobhelper.ability.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 个人能力库条目。
 *
 * <p>detailJson 保存六类条目各自的结构化明细，避免为每一种类型拆表。
 * 能力条目是简历合成与 AI 诊断的唯一事实源。</p>
 */
@Getter
@Setter
@TableName("ability_entry")
public class AbilityEntry {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String entryType;
    private String title;
    private String detailJson;
    private String source;
    private String status;
    private Integer enabled;
    private Integer sortNo;
    private Long sourceResumeId;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
