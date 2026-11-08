package com.qznu.jobhelper.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qznu.jobhelper.resume.entity.UserResume;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 简历版本数据访问。
 */
@Mapper
public interface UserResumeMapper extends BaseMapper<UserResume> {

    /**
     * 绕过逻辑删除过滤读取原始版本行，用于实现重复删除 no-op。
     */
    @Select("""
            SELECT *
            FROM user_resume
            WHERE id = #{id}
            LIMIT 1
            """)
    UserResume selectAnyById(@Param("id") Long id);
}
