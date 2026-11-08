package com.qznu.jobhelper.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qznu.jobhelper.task.entity.AiCallLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 调用日志数据访问。
 */
@Mapper
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {
}
