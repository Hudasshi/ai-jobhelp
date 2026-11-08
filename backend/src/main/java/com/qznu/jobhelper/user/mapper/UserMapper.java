package com.qznu.jobhelper.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qznu.jobhelper.user.entity.User;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 注册查重必须包含已注销用户，因为唯一键覆盖历史行，注销后用户名不可复用。
     */
    @Select("""
            SELECT COUNT(*)
            FROM `user`
            WHERE username = #{username}
            """)
    long countByUsernameIncludingDeleted(@Param("username") String username);

    /**
     * 管理端审计口径需要读取已注销行，因此不能使用会附加 deleted=0 的 BaseMapper。
     */
    @Select("""
            SELECT id, username, password, nickname, email, role, status,
                   quota_override, quota_generation, ai_call_limit_override,
                   last_login_at, deleted, deleted_at, created_at, updated_at
            FROM `user`
            WHERE id = #{id}
            LIMIT 1
            """)
    User selectAnyById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM `user`")
    long countAllIncludingDeleted();

    /**
     * 登录查询绕过 @TableLogic，以便明确区分“不存在”和“已注销”。
     */
    @Select("""
            SELECT id, username, password, nickname, email, role, status,
                   quota_override, quota_generation, ai_call_limit_override,
                   last_login_at, deleted, deleted_at, created_at, updated_at
            FROM `user`
            WHERE username = #{username}
            LIMIT 1
            """)
    User selectByUsernameIncludingDeleted(@Param("username") String username);

    @Update("""
            UPDATE `user`
            SET last_login_at = #{at}, updated_at = #{at}
            WHERE id = #{id} AND status = 1 AND deleted = 0
            """)
    int updateLastLoginAt(@Param("id") Long id, @Param("at") LocalDateTime at);

    @Update("""
            UPDATE `user`
            SET password = #{password}, updated_at = #{at}
            WHERE id = #{id} AND status = 1 AND deleted = 0
            """)
    int updatePassword(@Param("id") Long id,
                       @Param("password") String password,
                       @Param("at") LocalDateTime at);

    @Update("""
            UPDATE `user`
            SET status = #{status}, updated_at = #{at}
            WHERE id = #{id} AND deleted = 0
            """)
    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("at") LocalDateTime at);

    @Update("""
            UPDATE `user`
            SET quota_override = #{override},
                quota_generation = quota_generation + 1,
                updated_at = #{at}
            WHERE id = #{id} AND deleted = 0
            """)
    int updateQuotaOverride(@Param("id") Long id,
                            @Param("override") Integer override,
                            @Param("at") LocalDateTime at);

    @Update("""
            UPDATE `user`
            SET deleted = 1, deleted_at = #{at}, updated_at = #{at}
            WHERE id = #{id} AND deleted = 0
            """)
    int markDeleted(@Param("id") Long id, @Param("at") LocalDateTime at);
}
