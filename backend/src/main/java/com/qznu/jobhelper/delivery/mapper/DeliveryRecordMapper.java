package com.qznu.jobhelper.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qznu.jobhelper.delivery.entity.DeliveryRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 投递记录数据访问。
 */
@Mapper
public interface DeliveryRecordMapper extends BaseMapper<DeliveryRecord> {

    /**
     * 绕过 MyBatis-Plus 的逻辑删除过滤读取原始行。
     *
     * <p>删除接口需要区分“从未存在”和“已经删除后重放”：
     * 前者返回 404，后者按契约返回成功 no-op。</p>
     */
    @Select("""
            SELECT *
            FROM delivery_record
            WHERE id = #{id}
            LIMIT 1
            """)
    DeliveryRecord selectAnyById(@Param("id") Long id);
}
