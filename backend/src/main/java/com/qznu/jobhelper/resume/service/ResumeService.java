package com.qznu.jobhelper.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qznu.jobhelper.ability.entity.AbilityEntry;
import com.qznu.jobhelper.ability.mapper.AbilityEntryMapper;
import com.qznu.jobhelper.common.exception.BizException;
import com.qznu.jobhelper.common.exception.ForbiddenException;
import com.qznu.jobhelper.common.util.JsonUtils;
import com.qznu.jobhelper.delivery.entity.JobTarget;
import com.qznu.jobhelper.delivery.mapper.JobTargetMapper;
import com.qznu.jobhelper.diagnosis.entity.ResumeDiagnoseRecord;
import com.qznu.jobhelper.diagnosis.mapper.ResumeDiagnoseRecordMapper;
import com.qznu.jobhelper.resume.dto.GenerateResumeRequest;
import com.qznu.jobhelper.resume.dto.ResumeSaveRequest;
import com.qznu.jobhelper.resume.entity.UserResume;
import com.qznu.jobhelper.resume.mapper.UserResumeMapper;
import com.qznu.jobhelper.support.file.FileStorage;
import com.qznu.jobhelper.task.TaskService;
import com.qznu.jobhelper.task.entity.AiTask;
import com.qznu.jobhelper.user.entity.User;
import com.qznu.jobhelper.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 简历解析、生成、保存与版本管理。
 */
@Service
public class ResumeService {

    private final UserResumeMapper resumeMapper;
    private final AbilityEntryMapper abilityMapper;
    private final JobTargetMapper jobTargetMapper;
    private final ResumeDiagnoseRecordMapper diagnoseMapper;
    private final UserMapper userMapper;
    private final FileStorage fileStorage;
    private final TaskService taskService;
    private final JsonUtils jsonUtils;
    private final Clock clock;

    public ResumeService(UserResumeMapper resumeMapper,
                         AbilityEntryMapper abilityMapper,
                         JobTargetMapper jobTargetMapper,
                         ResumeDiagnoseRecordMapper diagnoseMapper,
                         UserMapper userMapper,
                         FileStorage fileStorage,
                         TaskService taskService,
                         JsonUtils jsonUtils,
                         Clock clock) {
        this.resumeMapper = resumeMapper;
        this.abilityMapper = abilityMapper;
        this.jobTargetMapper = jobTargetMapper;
        this.diagnoseMapper = diagnoseMapper;
        this.userMapper = userMapper;
        this.fileStorage = fileStorage;
        this.taskService = taskService;
        this.jsonUtils = jsonUtils;
        this.clock = clock;
    }

    /**
     * 上传文件先落盘并创建解析中的简历版本，再异步提交解析任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> parseUpload(Long userId, MultipartFile file) {
        FileStorage.StoredFile stored = fileStorage.store(file);
        LocalDateTime now = LocalDateTime.now(clock);
        String baseName = stored.originalName();
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }
        UserResume resume = new UserResume();
        resume.setUserId(userId);
        resume.setName(limit(baseName.isBlank() ? "上传简历" : baseName, 100));
        resume.setSourceType("upload");
        resume.setStatus("parsing");
        resume.setFilePath(stored.relativePath());
        resume.setSyncStatus(0);
        resume.setDeleted(0);
        resume.setCreatedAt(now);
        resume.setUpdatedAt(now);
        resumeMapper.insert(resume);

        Long taskId = taskService.submit(userId, "resume_parse", resume.getId(), Map.of(
                "resumeId", resume.getId(),
                "filePath", stored.relativePath(),
                "extension", stored.extension(),
                "fileName", stored.originalName()
        ));
        return Map.of("taskId", taskId);
    }

    /**
     * 冻结当前能力条目内容到任务快照，Worker 不再反查能力库。
     */
    public Map<String, Object> generateDraft(Long userId, GenerateResumeRequest request) {
        List<Long> ids = request.entryIds().stream().distinct().toList();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Long id : ids) {
            AbilityEntry entry = abilityMapper.selectById(id);
            if (entry == null || !entry.getUserId().equals(userId)) {
                throw ForbiddenException.forbidden();
            }
            if (!"confirmed".equals(entry.getStatus()) || !Integer.valueOf(1).equals(entry.getEnabled())) {
                throw BizException.badRequest("存在未确认或已停用的能力条目");
            }
            entries.add(Map.of(
                    "entryId", entry.getId(),
                    "entryType", entry.getEntryType(),
                    "title", entry.getTitle(),
                    "text", detailText(entry)
            ));
        }

        String targetPosition = "";
        if (request.jobTargetId() != null) {
            JobTarget target = jobTargetMapper.selectById(request.jobTargetId());
            if (target == null || !target.getUserId().equals(userId)) {
                throw ForbiddenException.forbidden();
            }
            targetPosition = target.getCompany() + " · " + target.getPosition();
        }
        User user = userMapper.selectById(userId);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("entryIds", ids);
        snapshot.put("entries", entries);
        snapshot.put("nickname", user == null ? "个人简历" : user.getNickname());
        snapshot.put("targetPosition", targetPosition);
        snapshot.put("jdText", request.jdText() == null ? "" : request.jdText());
        snapshot.put("jobTargetId", request.jobTargetId());
        Long taskId = taskService.submit(userId, "resume_generate", null, snapshot);
        return Map.of("taskId", taskId);
    }

    /**
     * 三路来源统一落成不可变版本快照。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveVersion(Long userId, ResumeSaveRequest request) {
        return switch (request.sourceType()) {
            case "generate" -> saveGenerated(userId, request);
            case "upload" -> saveUploaded(userId, request);
            case "diagnose" -> saveDiagnosed(userId, request);
            default -> throw BizException.badRequest("不支持的简历来源类型");
        };
    }

    private Map<String, Object> saveGenerated(Long userId, ResumeSaveRequest request) {
        if (request.draftTaskId() == null) {
            throw BizException.badRequest("缺少草稿任务ID");
        }
        AiTask task = taskService.requireOwnedTask(userId, request.draftTaskId());
        if (!"resume_generate".equals(task.getTaskType()) || !"SUCCESS".equals(task.getStatus())) {
            throw BizException.badRequest("草稿任务尚未成功");
        }
        Map<String, Object> result = jsonUtils.readMap(task.getResultJson());
        UserResume resume = new UserResume();
        resume.setUserId(userId);
        resume.setName(request.name().trim());
        resume.setSourceType("generate");
        resume.setStatus("ready");
        resume.setTargetPosition(blankToNull(request.targetPosition()));
        resume.setContentJson(jsonUtils.write(result.get("items")));
        resume.setContentMd(String.valueOf(result.getOrDefault("contentMd", "")));
        resume.setSyncStatus(0);
        resume.setDeleted(0);
        resume.setCreatedAt(LocalDateTime.now(clock));
        resume.setUpdatedAt(LocalDateTime.now(clock));
        resumeMapper.insert(resume);
        return toData(resume);
    }

    private Map<String, Object> saveUploaded(Long userId, ResumeSaveRequest request) {
        /*
         * upload 分支表示“用当前能力库重新合成一个版本”，不是任意挑几条。
         * 请求集合必须与当前全部 confirmed+enabled 条目完全一致，否则用户在表单
         * 打开后能力库已经变化，静默部分合成会得到与页面不一致的历史版本。
         */
        List<AbilityEntry> available = abilityMapper.selectList(
                new LambdaQueryWrapper<AbilityEntry>()
                        .eq(AbilityEntry::getUserId, userId)
                        .eq(AbilityEntry::getStatus, "confirmed")
                        .eq(AbilityEntry::getEnabled, 1)
                        .orderByAsc(AbilityEntry::getSortNo)
                        .orderByAsc(AbilityEntry::getId));
        Set<Long> requested = new LinkedHashSet<>(
                request.entryIds() == null ? List.of() : request.entryIds());
        Set<Long> actual = new LinkedHashSet<>(
                available.stream().map(AbilityEntry::getId).toList());
        if (!requested.equals(actual)) {
            throw BizException.badRequest("条目集合已变化，请刷新后重试");
        }

        UserResume source = null;
        if (request.sourceResumeId() != null) {
            source = requireOwned(userId, request.sourceResumeId());
            if (!"upload".equals(source.getSourceType())) {
                throw BizException.badRequest("来源版本必须为上传简历");
            }
        }
        List<Map<String, Object>> snapshot = new ArrayList<>();
        int order = 1;
        for (AbilityEntry entry : available) {
            snapshot.add(item(entry, order++));
        }
        User user = userMapper.selectById(userId);
        UserResume resume = new UserResume();
        resume.setUserId(userId);
        resume.setName(request.name().trim());
        resume.setSourceType("upload");
        resume.setStatus("ready");
        resume.setTargetPosition(blankToNull(request.targetPosition()));
        resume.setContentJson(jsonUtils.write(snapshot));
        resume.setContentMd(markdown(user == null ? "个人简历" : user.getNickname(),
                request.targetPosition(), snapshot));
        // 新版本只复制可追溯原文，不复制物理文件路径，避免两个版本共享同一文件。
        resume.setFilePath(null);
        resume.setRawText(source == null ? null : source.getRawText());
        resume.setSyncStatus(0);
        resume.setDeleted(0);
        resume.setCreatedAt(LocalDateTime.now(clock));
        resume.setUpdatedAt(LocalDateTime.now(clock));
        resumeMapper.insert(resume);
        return toData(resume);
    }

    private Map<String, Object> saveDiagnosed(Long userId, ResumeSaveRequest request) {
        if (request.diagnoseId() == null) {
            throw BizException.badRequest("缺少诊断ID");
        }
        ResumeDiagnoseRecord record = diagnoseMapper.selectById(request.diagnoseId());
        if (record == null) {
            throw BizException.notFound("诊断报告不存在或已过期");
        }
        if (record.getUserId() == null || !record.getUserId().equals(userId)) {
            throw ForbiddenException.forbidden();
        }
        Map<String, Object> report = jsonUtils.readMap(record.getReportJson());
        Object source = report.get("sourceSnapshot");
        if (!(source instanceof List<?> list) || list.isEmpty()) {
            throw BizException.badRequest("诊断报告已过期，不能合成简历");
        }
        Set<Long> adoptedIds = new LinkedHashSet<>();
        Object rawItems = report.get("items");
        if (rawItems instanceof List<?> items) {
            for (Object value : items) {
                if (value instanceof Map<?, ?> item
                        && Boolean.TRUE.equals(item.get("adopted"))
                        && "rewritten".equals(String.valueOf(item.get("verdict")))) {
                    Object entryId = item.get("entryId");
                    if (entryId != null) {
                        adoptedIds.add(Long.valueOf(String.valueOf(entryId)));
                    }
                }
            }
        }
        if (adoptedIds.isEmpty()) {
            throw BizException.badRequest("请至少采纳一条诊断建议后再合成简历");
        }
        Map<Long, String> rewrites = new LinkedHashMap<>();
        if (rawItems instanceof List<?> items) {
            for (Object value : items) {
                if (value instanceof Map<?, ?> item
                        && Boolean.TRUE.equals(item.get("adopted"))
                        && "rewritten".equals(String.valueOf(item.get("verdict")))
                        && item.get("entryId") != null) {
                    rewrites.put(Long.valueOf(String.valueOf(item.get("entryId"))),
                            String.valueOf(item.get("rewrite")));
                }
            }
        }
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> row)) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            row.forEach((key, content) -> copy.put(String.valueOf(key), content));
            Object rawEntryId = copy.get("entryId");
            if (rawEntryId != null) {
                Long entryId = Long.valueOf(String.valueOf(rawEntryId));
                String rewrite = rewrites.get(entryId);
                if (rewrite != null) {
                    copy.put("text", rewrite);
                }
            }
            snapshot.add(copy);
        }
        UserResume resume = new UserResume();
        resume.setUserId(userId);
        resume.setName(request.name().trim());
        resume.setSourceType("diagnose");
        resume.setStatus("ready");
        resume.setTargetPosition(blankToNull(request.targetPosition()));
        resume.setContentJson(jsonUtils.write(snapshot));
        User user = userMapper.selectById(userId);
        resume.setContentMd(markdown(user == null ? "个人简历" : user.getNickname(),
                request.targetPosition(), snapshot));
        resume.setSyncStatus(0);
        resume.setDeleted(0);
        resume.setCreatedAt(LocalDateTime.now(clock));
        resume.setUpdatedAt(LocalDateTime.now(clock));
        resumeMapper.insert(resume);
        return toData(resume);
    }

    public Map<String, Object> list(Long userId, long page, long size) {
        LambdaQueryWrapper<UserResume> query = new LambdaQueryWrapper<UserResume>()
                .eq(UserResume::getUserId, userId)
                .orderByDesc(UserResume::getUpdatedAt);
        long total = resumeMapper.selectCount(query);
        query.last("LIMIT " + size + " OFFSET " + ((page - 1) * size));
        List<Map<String, Object>> records = resumeMapper.selectList(query).stream()
                .map(this::toData)
                .toList();
        return Map.of(
                "records", records,
                "total", total,
                "page", page,
                "size", size
        );
    }

    public Map<String, Object> get(Long userId, Long id) {
        return toData(requireOwned(userId, id));
    }

    public void delete(Long userId, Long id) {
        UserResume resume = resumeMapper.selectAnyById(id);
        if (resume == null) {
            throw BizException.notFound("简历版本不存在");
        }
        if (!resume.getUserId().equals(userId)) {
            throw ForbiddenException.forbidden();
        }
        if (Integer.valueOf(1).equals(resume.getDeleted())) {
            return;
        }
        if (resumeMapper.deleteById(id) == 0) {
            UserResume latest = resumeMapper.selectAnyById(id);
            if (latest != null && Integer.valueOf(1).equals(latest.getDeleted())) {
                return;
            }
            throw new IllegalStateException("简历版本删除失败");
        }
        // 断开来历引用，避免能力条目继续指向用户已删除的历史版本。
        abilityMapper.update(null, new LambdaUpdateWrapper<AbilityEntry>()
                .eq(AbilityEntry::getUserId, userId)
                .eq(AbilityEntry::getSourceResumeId, id)
                .set(AbilityEntry::getSourceResumeId, null)
                .set(AbilityEntry::getUpdatedAt, LocalDateTime.now(clock)));
    }

    public UserResume requireOwned(Long userId, Long id) {
        UserResume resume = resumeMapper.selectById(id);
        if (resume == null) {
            throw BizException.notFound("简历版本不存在");
        }
        if (!resume.getUserId().equals(userId)) {
            throw ForbiddenException.forbidden();
        }
        return resume;
    }

    private Map<String, Object> toData(UserResume resume) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", resume.getId());
        data.put("name", resume.getName());
        data.put("sourceType", resume.getSourceType());
        data.put("status", resume.getStatus());
        data.put("targetPosition", resume.getTargetPosition());
        data.put("syncStatus", resume.getSyncStatus());
        data.put("createdAt", resume.getCreatedAt());
        data.put("updatedAt", resume.getUpdatedAt());
        data.put("items", jsonUtils.readList(resume.getContentJson()));
        data.put("contentMd", resume.getContentMd());
        data.put("skipped", List.of());
        return data;
    }

    private Map<String, Object> item(AbilityEntry entry, int order) {
        return Map.of(
                "entryId", entry.getId(),
                "order", order,
                "title", entry.getTitle(),
                "text", detailText(entry)
        );
    }

    private String detailText(AbilityEntry entry) {
        Map<String, Object> detail = jsonUtils.readMap(entry.getDetailJson());
        Object description = detail.get("description");
        if (description != null) {
            return String.valueOf(description);
        }
        return detail.entrySet().stream()
                .map(item -> item.getKey() + "：" + item.getValue())
                .reduce((left, right) -> left + "；" + right)
                .orElse(entry.getTitle());
    }

    private String markdown(String nickname, String target, List<Map<String, Object>> items) {
        StringBuilder builder = new StringBuilder("# ").append(nickname).append("\n\n");
        if (target != null && !target.isBlank()) {
            builder.append("## 求职意向\n").append(target).append("\n\n");
        }
        for (Map<String, Object> item : items) {
            builder.append("## ").append(item.get("title")).append("\n")
                    .append("- ").append(item.get("text")).append("\n\n");
        }
        return builder.toString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
