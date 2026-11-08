package com.qznu.jobhelper.ability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qznu.jobhelper.ability.dto.AbilityRequest;
import com.qznu.jobhelper.ability.entity.AbilityEntry;
import com.qznu.jobhelper.ability.mapper.AbilityEntryMapper;
import com.qznu.jobhelper.common.exception.BizException;
import com.qznu.jobhelper.common.exception.ForbiddenException;
import com.qznu.jobhelper.common.util.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 个人能力库业务。
 */
@Service
public class AbilityService {

    private static final Set<String> TYPES = Set.of(
            "education", "internship", "project", "skill", "certificate", "other");

    private final AbilityEntryMapper abilityMapper;
    private final JsonUtils jsonUtils;
    private final Clock clock;

    public AbilityService(AbilityEntryMapper abilityMapper, JsonUtils jsonUtils, Clock clock) {
        this.abilityMapper = abilityMapper;
        this.jsonUtils = jsonUtils;
        this.clock = clock;
    }

    /**
     * 六类条目的前端字段说明。前端据此动态生成表单，避免硬编码重复规则。
     */
    public Map<String, Object> fieldSpec() {
        List<Map<String, Object>> types = new ArrayList<>();
        types.add(spec("education", "教育经历",
                field("school", "学校", "text", true),
                field("major", "专业", "text", true),
                field("degree", "学历", "text", true),
                field("period", "时间", "text", true),
                field("gpa", "GPA", "text", false)));
        types.add(spec("internship", "实习经历",
                field("company", "公司", "text", true),
                field("role", "岗位", "text", true),
                field("tech", "技术栈", "text", false),
                field("period", "时间", "text", false),
                field("description", "工作内容", "textarea", true),
                field("result", "成果", "textarea", false)));
        types.add(spec("project", "项目经历",
                field("name", "项目名称", "text", true),
                field("role", "角色", "text", true),
                field("tech", "技术栈", "text", false),
                field("period", "时间", "text", false),
                field("description", "项目描述", "textarea", true),
                field("result", "成果", "textarea", false)));
        types.add(spec("skill", "技能",
                field("level", "掌握程度", "text", true),
                field("years", "使用年限", "number", false)));
        types.add(spec("certificate", "证书",
                field("level", "级别", "text", false),
                field("date", "取得时间", "text", false)));
        types.add(spec("other", "其他",
                field("description", "说明", "textarea", true)));
        return Map.of("entryTypes", types);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long userId, AbilityRequest request) {
        requireType(request.entryType());
        LocalDateTime now = LocalDateTime.now(clock);
        AbilityEntry entry = new AbilityEntry();
        entry.setUserId(userId);
        entry.setEntryType(request.entryType());
        entry.setTitle(request.title().trim());
        entry.setDetailJson(jsonUtils.write(request.detail()));
        entry.setSource("manual");
        entry.setStatus("confirmed");
        entry.setEnabled(1);
        entry.setSortNo(request.sortOrder() == null
                ? abilityMapper.nextSortNo(userId) : request.sortOrder());
        entry.setDeleted(0);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        abilityMapper.insert(entry);
        return toData(entry);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(Long userId, AbilityRequest request) {
        if (request.id() == null) {
            throw BizException.badRequest("缺少条目ID");
        }
        AbilityEntry entry = requireOwned(userId, request.id());
        requireType(request.entryType());
        entry.setEntryType(request.entryType());
        entry.setTitle(request.title().trim());
        entry.setDetailJson(jsonUtils.write(request.detail()));
        if (request.sortOrder() != null) {
            entry.setSortNo(request.sortOrder());
        }
        entry.setUpdatedAt(LocalDateTime.now(clock));
        abilityMapper.updateById(entry);
        return toData(entry);
    }

    public Map<String, Object> list(Long userId, String entryType, String status,
                                    Integer enabled, String keyword,
                                    long page, long size) {
        LambdaQueryWrapper<AbilityEntry> query = new LambdaQueryWrapper<AbilityEntry>()
                .eq(AbilityEntry::getUserId, userId)
                .eq(entryType != null && !entryType.isBlank(), AbilityEntry::getEntryType, entryType)
                .eq(status != null && !status.isBlank(), AbilityEntry::getStatus, status)
                .eq(enabled != null, AbilityEntry::getEnabled, enabled)
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like(AbilityEntry::getTitle, keyword)
                        .or()
                        .like(AbilityEntry::getDetailJson, keyword))
                .orderByAsc(AbilityEntry::getSortNo)
                .orderByDesc(AbilityEntry::getCreatedAt);

        long total = abilityMapper.selectCount(query);
        query.last("LIMIT " + size + " OFFSET " + ((page - 1) * size));
        List<Map<String, Object>> records = abilityMapper.selectList(query).stream()
                .map(this::toData)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    public Map<String, Object> get(Long userId, Long id) {
        return toData(requireOwned(userId, id));
    }

    public void delete(Long userId, Long id) {
        AbilityEntry entry = abilityMapper.selectAnyById(id);
        if (entry == null) {
            throw BizException.notFound("能力条目不存在");
        }
        if (!entry.getUserId().equals(userId)) {
            throw ForbiddenException.forbidden();
        }
        if (Integer.valueOf(1).equals(entry.getDeleted())) {
            return;
        }
        if (abilityMapper.deleteById(id) == 0) {
            AbilityEntry latest = abilityMapper.selectAnyById(id);
            if (latest != null && Integer.valueOf(1).equals(latest.getDeleted())) {
                return;
            }
            throw new IllegalStateException("能力条目删除失败");
        }
    }

    public void setEnabled(Long userId, Long id, Integer enabled) {
        AbilityEntry entry = requireOwned(userId, id);
        if (!"confirmed".equals(entry.getStatus())) {
            throw BizException.badRequest("待确认条目不能启停");
        }
        entry.setEnabled(enabled);
        entry.setUpdatedAt(LocalDateTime.now(clock));
        abilityMapper.updateById(entry);
    }

    public void confirm(Long userId, Long id) {
        AbilityEntry entry = requireOwned(userId, id);
        if ("confirmed".equals(entry.getStatus())) {
            return;
        }
        if (!"pending".equals(entry.getStatus())) {
            throw BizException.badRequest("只有待确认条目可以确认");
        }
        entry.setStatus("confirmed");
        entry.setUpdatedAt(LocalDateTime.now(clock));
        abilityMapper.updateById(entry);
    }

    public void reject(Long userId, Long id) {
        AbilityEntry entry = abilityMapper.selectAnyById(id);
        if (entry == null) {
            throw BizException.notFound("能力条目不存在");
        }
        if (!entry.getUserId().equals(userId)) {
            throw ForbiddenException.forbidden();
        }
        if (Integer.valueOf(1).equals(entry.getDeleted())) {
            throw BizException.badRequest("只有待确认条目可以拒绝");
        }
        if (!"pending".equals(entry.getStatus())) {
            throw BizException.badRequest("只有待确认条目可以拒绝");
        }
        abilityMapper.deleteById(entry.getId());
    }

    public AbilityEntry requireOwned(Long userId, Long id) {
        AbilityEntry entry = abilityMapper.selectById(id);
        if (entry == null) {
            throw BizException.notFound("能力条目不存在");
        }
        if (!entry.getUserId().equals(userId)) {
            throw ForbiddenException.forbidden();
        }
        return entry;
    }

    private void requireType(String type) {
        if (!TYPES.contains(type)) {
            throw BizException.badRequest("不支持的能力条目类型");
        }
    }

    private Map<String, Object> toData(AbilityEntry entry) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", entry.getId());
        data.put("entryType", entry.getEntryType());
        data.put("title", entry.getTitle());
        data.put("detail", jsonUtils.readMap(entry.getDetailJson()));
        data.put("source", entry.getSource());
        data.put("status", entry.getStatus());
        data.put("enabled", entry.getEnabled());
        data.put("sortOrder", entry.getSortNo());
        data.put("sourceResumeId", entry.getSourceResumeId());
        data.put("sourceRawText", null);
        data.put("createdAt", entry.getCreatedAt());
        data.put("updatedAt", entry.getUpdatedAt());
        return data;
    }

    private Map<String, Object> spec(String entryType, String label, Map<String, Object>... fields) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("entryType", entryType);
        map.put("label", label);
        map.put("fields", List.of(fields));
        return map;
    }

    private Map<String, Object> field(String key, String label, String type, boolean required) {
        return Map.of(
                "key", key,
                "label", label,
                "type", type,
                "required", required
        );
    }
}
