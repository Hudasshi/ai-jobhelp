package com.qznu.jobhelper.support.config.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 可热更新的系统配置。数据库值优先于 application.yml。
 */
@Getter
@Setter
@TableName("system_config")
public class SystemConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String configKey;
    private String configValue;
    private String remark;
    private LocalDateTime updatedAt;
}
