package com.qznu.jobhelper.diagnosis.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qznu.jobhelper.common.exception.BizException;
import com.qznu.jobhelper.common.exception.ForbiddenException;
import com.qznu.jobhelper.common.util.JsonUtils;
import com.qznu.jobhelper.delivery.entity.DeliveryRecord;
import com.qznu.jobhelper.delivery.entity.JobTarget;
import com.qznu.jobhelper.delivery.mapper.DeliveryRecordMapper;
import com.qznu.jobhelper.delivery.mapper.JobTargetMapper;
import com.qznu.jobhelper.diagnosis.dto.AdoptRequest;
import com.qznu.jobhelper.diagnosis.dto.CreateDiagnosisRequest;
import com.qznu.jobhelper.diagnosis.entity.ResumeDiagnoseRecord;
import com.qznu.jobhelper.diagnosis.mapper.ResumeDiagnoseRecordMapper;
import com.qznu.jobhelper.resume.entity.UserResume;
import com.qznu.jobhelper.resume.service.ResumeService;
import com.qznu.jobhelper.task.TaskService;
import com.qznu.jobhelper.user.entity.User;
import com.qznu.jobhelper.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 简历诊断的创建、查询与建议采纳。
 *
 * <p>诊断创建时把简历条目和 JD 冻结进任务输入快照，异步 Worker 不再回读可能已经
 * 修改的简历；报告生成后再以 sourceSnapshot 固化，保证历史报告可复现。</p>
 */
@Service
public class DiagnosisService {

    private static final int KEEP_DAYS = 180;

    private final ResumeService resumeService;
    private final DeliveryRecordMapper deliveryMapper;
    private final JobTargetMapper jobTargetMapper;
    private final ResumeDiagnoseRecordMapper diagnoseMapper;
    private final UserMapper userMapper;
    private final TaskService taskService;
    private final JsonUtils jsonUtils;
    private final Clock clock;

    public DiagnosisService(ResumeService resumeService,
                            DeliveryRecordMapper deliveryMapper,
                            JobTargetMapper jobTargetMapper,
                            ResumeDiagnoseRecordMapper diagnoseMapper,
                            UserMapper userMapper,
                            TaskService taskService,
                            JsonUtils jsonUtils,
                            Clock clock) {
        this.resumeService = resumeService;
        this.deliveryMapper = deliveryMapper;
        this.jobTargetMapper = jobTargetMapper;
        this.diagnoseMapper = diagnoseMapper;
        this.userMapper = userMapper;
        this.taskService = taskService;
        this.jsonUtils = jsonUtils;
        this.clock = clock;
    }

    public Map<String, Object> create(Long userId, CreateDiagnosisRequest request) {
        UserResume resume = resumeService.requireOwned(userId, request.resumeId());
        if (!"ready".equals(resume.getStatus())) {
            throw BizException.badRequest("简历版本尚未就绪");
        }
        List<Map<String, Object>> source = jsonUtils.readList(resume.getContentJson());
        if (source.isEmpty()) {
            throw BizException.badRequest("简历内容为空，无法诊断");
        }

        Long jobTargetId = null;
        String position = resume.getTargetPosition();
        if (request.deliveryId() != null) {
            DeliveryRecord delivery = deliveryMapper.selectById(request.deliveryId());
            if (delivery == null) {
                throw BizException.notFound("投递记录不存在");
            }
            if (!delivery.getUserId().equals(userId)) {
                throw ForbiddenException.forbidden();
            }
            jobTargetId = delivery.getJobTargetId();
            JobTarget target = jobTargetMapper.selectById(jobTargetId);
            if (target != null) {
                position = target.getCompany() + " · " + target.getPosition();
            }
        }

        User user = userMapper.selectById(userId);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("resumeId", resume.getId());
        snapshot.put("deliveryId", request.deliveryId());
        snapshot.put("jobTargetId", jobTargetId);
        snapshot.put("jd", request.jd().trim());
        snapshot.put("sourceSnapshot", source);
        snapshot.put("usernameSnapshot", user == null ? "" : user.getUsername());
        snapshot.put("positionSnapshot", position == null ? "" : position);
        Long taskId = taskService.submit(userId, "diagnose", null, snapshot);
        return Map.of("taskId", taskId);
    }

    public Map<String, Object> get(Long userId, Long id) {
        ResumeDiagnoseRecord record = requireOwned(userId, id);
        if (isExpired(record)) {
            throw BizException.notFound("诊断报告不存在或已过期");
        }
        boolean failed = "failed".equals(record.getStatus());
        if (!failed && (record.getReportJson() == null || record.getReportJson().isBlank())) {
            throw BizException.notFound("诊断报告不存在或已过期");
        }
        return toDetail(record);
    }

    public Map<String, Object> page(Long userId, String status, long page, long size) {
        if (status != null && !status.isBlank()
                && !List.of("success", "degraded", "failed").contains(status)) {
            throw BizException.badRequest("诊断状态不合法");
        }
        LambdaQueryWrapper<ResumeDiagnoseRecord> query =
                new LambdaQueryWrapper<ResumeDiagnoseRecord>()
                        .eq(ResumeDiagnoseRecord::getUserId, userId)
                        .eq(status != null && !status.isBlank(),
                                ResumeDiagnoseRecord::getStatus, status)
                        .orderByDesc(ResumeDiagnoseRecord::getCreatedAt)
                        .orderByDesc(ResumeDiagnoseRecord::getId);
        long total = diagnoseMapper.selectCount(query);
        query.last("LIMIT " + size + " OFFSET " + ((page - 1) * size));
        List<Map<String, Object>> records = diagnoseMapper.selectList(query).stream()
                .map(this::toSummary)
                .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /**
     * 采纳标记直接存进 report_json.items，不新增表。这样一份历史报告的“用户是否
     * 采纳”与其原建议永远绑定，不会因为能力库条目后续被删除而失去上下文。
     */
    public Map<String, Object> adopt(Long userId, Long id, Long entryId,
                                     AdoptRequest request) {
        ResumeDiagnoseRecord record = requireOwned(userId, id);
        if (isExpired(record) || record.getReportJson() == null) {
            throw BizException.notFound("诊断报告或建议条目不存在");
        }
        Map<String, Object> report = jsonUtils.readMap(record.getReportJson());
        List<Map<String, Object>> items = new ArrayList<>();
        Object rawItems = report.get("items");
        if (rawItems instanceof List<?> list) {
            for (Object value : list) {
                if (value instanceof Map<?, ?> row) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    row.forEach((key, content) -> copy.put(String.valueOf(key), content));
                    items.add(copy);
                }
            }
        }
        Map<String, Object> matched = items.stream()
                .filter(item -> String.valueOf(entryId).equals(String.valueOf(item.get("entryId"))))
                .findFirst()
                .orElseThrow(() -> BizException.notFound("诊断报告或建议条目不存在"));
        matched.put("adopted", request.adopted());
        report.put("items", items);
        record.setReportJson(jsonUtils.write(report));
        diagnoseMapper.updateById(record);

        long adoptedCount = items.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("adopted")))
                .count();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entryId", entryId);
        data.put("adopted", request.adopted());
        data.put("adoptedCount", adoptedCount);
        data.put("totalCount", items.size());
        return data;
    }

    private ResumeDiagnoseRecord requireOwned(Long userId, Long id) {
        ResumeDiagnoseRecord record = diagnoseMapper.selectById(id);
        if (record == null) {
            throw BizException.notFound("诊断报告不存在或已过期");
        }
        if (record.getUserId() == null || !record.getUserId().equals(userId)) {
            throw ForbiddenException.forbidden();
        }
        return record;
    }

    private boolean isExpired(ResumeDiagnoseRecord record) {
        return record.getCreatedAt() == null
                || record.getCreatedAt().isBefore(LocalDateTime.now(clock).minusDays(KEEP_DAYS));
    }

    private Map<String, Object> toDetail(ResumeDiagnoseRecord record) {
        /*
         * sourceSnapshot 是服务端复算和合成简历所需的内部快照，
         * 不属于 DiagnosisReportData 的 wire 字段。对外投影时显式剔除，
         * 防止把整份简历正文顺带泄露给管理端或普通响应抓包。
         */
        Map<String, Object> report = record.getReportJson() == null
                || record.getReportJson().isBlank()
                ? null : jsonUtils.readMap(record.getReportJson());
        if (report != null) {
            report.remove("sourceSnapshot");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", record.getId());
        data.put("resumeId", record.getResumeId());
        data.put("deliveryId", record.getDeliveryId());
        data.put("position", record.getPositionSnapshot());
        data.put("status", record.getStatus());
        data.put("degraded", "degraded".equals(record.getStatus()));
        data.put("failReason", record.getFailReason());
        data.put("report", report);
        data.put("costSeconds", record.getCostSeconds());
        data.put("tokens", record.getTokens());
        data.put("createdAt", record.getCreatedAt());
        return data;
    }

    private Map<String, Object> toSummary(ResumeDiagnoseRecord record) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", record.getId());
        data.put("position", record.getPositionSnapshot());
        data.put("score", record.getScore());
        data.put("status", record.getStatus());
        data.put("degraded", "degraded".equals(record.getStatus()));
        data.put("failReason", record.getFailReason());
        data.put("createdAt", record.getCreatedAt());
        return data;
    }
}
