package com.qznu.jobhelper.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 岗位目标。
 *
 * <p>同一用户的“公司 + 岗位”只保留一份 JD，投递、诊断和面试都复用该记录。</p>
 */
@Getter
@Setter
@TableName("job_target")
public class JobTarget {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String company;
    private String position;
    private String jdRaw;
    private String skills;
    private String highlights;
    private LocalDate deadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
