package com.qznu.jobhelper.support.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统审计日志，只记录动作元数据，不记录密码、Token 和业务正文。
 */
@Getter
@Setter
@TableName("system_log")
public class SystemLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String action;
    private Integer success;
    private String ip;
    private String remark;
    private LocalDateTime createdAt;
}
