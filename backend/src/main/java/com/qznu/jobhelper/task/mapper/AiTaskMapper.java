package com.qznu.jobhelper.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qznu.jobhelper.task.entity.AiTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI 任务数据访问。
 */
@Mapper
public interface AiTaskMapper extends BaseMapper<AiTask> {

    /**
     * 工作台通知只取最近任务元数据，不返回任务正文。
     */
    @Select("""
            SELECT id, user_id, task_type, biz_id, status, progress, fail_reason,
                   result_ref, NULL AS result_json, NULL AS input_snapshot,
                   quota_date, quota_generation, quota_state, refunded, read_flag,
                   created_at, started_at, finished_at, timeout_at
            FROM ai_task
            WHERE user_id = #{userId}
            ORDER BY created_at DESC
            LIMIT #{limit}
            """)
    List<AiTask> selectRecentNotices(@Param("userId") Long userId, @Param("limit") int limit);
}
