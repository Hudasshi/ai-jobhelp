package com.qznu.jobhelper.ability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qznu.jobhelper.ability.entity.AbilityEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 能力库数据访问。
 */
@Mapper
public interface AbilityEntryMapper extends BaseMapper<AbilityEntry> {

    /**
     * 查询包含已逻辑删除行的原始记录。
     *
     * <p>确认、拒绝和删除接口需要按契约区分“不存在”“他人资源”和
     * “本人已删除后重放”，不能直接使用会自动追加 deleted=0 的 BaseMapper。</p>
     */
    @Select("""
            SELECT *
            FROM ability_entry
            WHERE id = #{id}
            LIMIT 1
            """)
    AbilityEntry selectAnyById(@Param("id") Long id);

    /**
     * 计算下一个排序号，保证手工新增条目默认排在末尾。
     */
    @Select("""
            SELECT COALESCE(MAX(sort_no), 0) + 1
            FROM ability_entry
            WHERE user_id = #{userId} AND deleted = 0
            """)
    int nextSortNo(@Param("userId") Long userId);
}
