package com.qznu.jobhelper.user.entity;

/**
 * 用户账号实体。
 *
 * <p>password 始终保存 BCrypt 密文，任何接口都不得直接返回 User 对象。</p>
 */

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("user")
@Getter
@Setter
public class User {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String email;
    private String role;
    private Integer status;
    private Integer quotaOverride;
    private Long quotaGeneration;
    private Integer aiCallLimitOverride;
    private LocalDateTime lastLoginAt;

    @TableLogic
    private Integer deleted;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
