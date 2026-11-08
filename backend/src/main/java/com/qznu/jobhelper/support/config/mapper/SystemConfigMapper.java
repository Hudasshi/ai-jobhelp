package com.qznu.jobhelper.support.config.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qznu.jobhelper.support.config.entity.SystemConfig;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * 系统配置数据访问。
 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {

    @Insert("""
            INSERT INTO system_config (config_key, config_value, remark)
            VALUES (#{configKey}, #{configValue}, #{remark})
            ON DUPLICATE KEY UPDATE
                config_value = VALUES(config_value),
                remark = VALUES(remark),
                updated_at = NOW()
            """)
    int upsert(SystemConfig config);

    @Update("""
            UPDATE system_config
            SET config_value = #{value}, updated_at = NOW()
            WHERE config_key = #{key}
            """)
    int updateValue(String key, String value);
}
