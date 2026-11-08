package com.qznu.jobhelper.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qznu.jobhelper.interview.entity.MockInterviewRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 模拟面试数据访问。
 */
@Mapper
public interface MockInterviewRecordMapper extends BaseMapper<MockInterviewRecord> {
}
