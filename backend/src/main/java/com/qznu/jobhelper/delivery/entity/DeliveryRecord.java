package com.qznu.jobhelper.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 投递记录。
 *
 * <p>公司、岗位和 JD 不在此表冗余，统一回读 job_target；
 * statusHistory 用 JSON 保存每次状态变更，避免新增第 12 张表。</p>
 */
@Getter
@Setter
@TableName("delivery_record")
public class DeliveryRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long jobTargetId;
    private String status;
    private LocalDate deliveryDate;
    private LocalDateTime examTime;
    private LocalDateTime interviewTime;
    private LocalDate deadline;
    private String skillReq;
    private String remark;
    private String statusHistory;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
