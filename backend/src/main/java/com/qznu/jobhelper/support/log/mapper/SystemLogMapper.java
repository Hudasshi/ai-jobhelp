package com.qznu.jobhelper.support.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qznu.jobhelper.support.log.entity.SystemLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统审计日志数据访问。
 */
@Mapper
public interface SystemLogMapper extends BaseMapper<SystemLog> {
}
