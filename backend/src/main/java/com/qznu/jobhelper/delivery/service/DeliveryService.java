package com.qznu.jobhelper.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qznu.jobhelper.common.exception.BizException;
import com.qznu.jobhelper.common.exception.ForbiddenException;
import com.qznu.jobhelper.common.util.JsonUtils;
import com.qznu.jobhelper.delivery.dto.CreateDeliveryRequest;
import com.qznu.jobhelper.delivery.dto.DeliveryStatusRequest;
import com.qznu.jobhelper.delivery.dto.ParseJobDescriptionRequest;
import com.qznu.jobhelper.delivery.dto.UpdateDeliveryRequest;
import com.qznu.jobhelper.delivery.entity.DeliveryRecord;
import com.qznu.jobhelper.delivery.entity.JobTarget;
import com.qznu.jobhelper.delivery.mapper.DeliveryRecordMapper;
import com.qznu.jobhelper.delivery.mapper.JobTargetMapper;
import com.qznu.jobhelper.interview.entity.MockInterviewRecord;
import com.qznu.jobhelper.interview.mapper.MockInterviewRecordMapper;
import com.qznu.jobhelper.support.quota.QuotaService;
import com.qznu.jobhelper.task.AiGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 投递看板业务服务。
 *
 * <p>投递表和岗位目标表分开存储：岗位目标承载“公司 + 岗位 + JD”这种可复用的
 * 事实，投递记录承载用户针对某个岗位的一次求职过程。这样从投递发起诊断或模拟
 * 面试时，可以稳定引用同一份岗位上下文。</p>
 */
@Slf4j
@Service
public class DeliveryService {

    /** 六态迁移矩阵与冻结设计要求一致，不能由数据库配置动态改变。 */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "pending", Set.of("applied", "rejected"),
            "applied", Set.of("exam", "rejected"),
            "exam", Set.of("interview", "rejected"),
            "interview", Set.of("exam", "offer", "rejected"),
            "offer", Set.of(),
            "rejected", Set.of()
    );

    private static final Set<String> STATUSES = Set.of(
            "pending", "applied", "exam", "interview", "offer", "rejected");

    private static final List<String> KNOWN_SKILLS = List.of(
            "Java", "Spring Boot", "Spring Cloud", "MyBatis", "MySQL", "Redis",
            "Kafka", "RocketMQ", "RabbitMQ", "Go", "Python", "Vue", "JavaScript",
            "Docker", "Kubernetes", "Linux", "JVM", "JUC", "Netty", "ZooKeeper",
            "分布式系统", "幂等", "分库分表");

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(20\\d{2})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?");

    private final DeliveryRecordMapper deliveryMapper;
    private final JobTargetMapper jobTargetMapper;
    private final MockInterviewRecordMapper interviewMapper;
    private final QuotaService quotaService;
    private final AiGateway aiGateway;
    private final JsonUtils jsonUtils;
    private final Clock clock;

    public DeliveryService(DeliveryRecordMapper deliveryMapper,
                           JobTargetMapper jobTargetMapper,
                           MockInterviewRecordMapper interviewMapper,
                           QuotaService quotaService,
                           AiGateway aiGateway,
                           JsonUtils jsonUtils,
                           Clock clock) {
        this.deliveryMapper = deliveryMapper;
        this.jobTargetMapper = jobTargetMapper;
        this.interviewMapper = interviewMapper;
        this.quotaService = quotaService;
        this.aiGateway = aiGateway;
        this.jsonUtils = jsonUtils;
        this.clock = clock;
    }

    /** 用户看板一次性返回列表与统计，避免前端为了六列计数再发六次请求。 */
    public Map<String, Object> list(Long userId) {
        List<DeliveryRecord> records = deliveryMapper.selectList(
                new LambdaQueryWrapper<DeliveryRecord>()
                        .eq(DeliveryRecord::getUserId, userId)
                        .orderByDesc(DeliveryRecord::getUpdatedAt)
                        .orderByDesc(DeliveryRecord::getId));
        List<Map<String, Object>> projected = records.stream()
                .map(record -> toData(record, latestInterviewRecord(record.getId()), true))
                .toList();

        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : List.of("pending", "applied", "exam", "interview", "offer", "rejected")) {
            counts.put(status, records.stream().filter(row -> status.equals(row.getStatus())).count());
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("deliveryTotal", records.size());
        stats.put("statusCounts", counts);
        stats.put("interviewConversionRate", conversionRate(records));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", projected);
        data.put("stats", stats);
        return data;
    }

    public Map<String, Object> get(Long userId, Long id) {
        DeliveryRecord record = requireOwned(userId, id);
        return toData(record, latestInterviewRecord(id), true);
    }

    /**
     * 新建投递与岗位目标写入同一事务。岗位目标采用“用户 + 公司 + 岗位”唯一键，
     * 所以同一岗位再次投递时只刷新 JD，不制造重复岗位。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long userId, CreateDeliveryRequest request) {
        String status = normalizeStatus(request.status(), true);
        LocalDateTime now = LocalDateTime.now(clock);
        JobTarget target = upsertTarget(userId, request.company(), request.position(),
                request.jdRaw(), now);

        DeliveryRecord record = new DeliveryRecord();
        record.setUserId(userId);
        record.setJobTargetId(target.getId());
        record.setStatus(status);
        record.setDeliveryDate(request.deliveryDate());
        record.setExamTime(request.examTime());
        record.setInterviewTime(request.interviewTime());
        record.setDeadline(request.deadline());
        record.setSkillReq(blankToNull(request.skillReq()));
        record.setRemark(blankToNull(request.remark()));
        record.setStatusHistory(jsonUtils.write(List.of(historyItem(
                "pending", status, now))));
        record.setDeleted(0);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        deliveryMapper.insert(record);
        return toData(record, null, true);
    }

    /**
     * 编辑投递。状态字段若发生变化，必须仍然走六态矩阵，不能在编辑接口绕过状态机。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(Long userId, UpdateDeliveryRequest request) {
        DeliveryRecord record = requireOwned(userId, request.id());
        /*
         * status 未出现表示保持原状态；出现但为 null 是非法请求。
         * 同当前状态也必须走成功 no-op，不能落入状态迁移矩阵的对角线。
         */
        String nextStatus;
        if (!request.statusPresent()) {
            nextStatus = record.getStatus();
        } else if (request.status() == null) {
            throw BizException.badRequest("status字段不能为null");
        } else {
            nextStatus = normalizeStatus(request.status(), false);
        }
        if (!nextStatus.equals(record.getStatus())) {
            assertCanTransit(record.getStatus(), nextStatus);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        JobTarget currentTarget = jobTargetMapper.selectById(record.getJobTargetId());
        if (currentTarget == null || !currentTarget.getUserId().equals(userId)) {
            throw ForbiddenException.forbidden();
        }
        JobTarget target = resolveUpdateTarget(
                userId, currentTarget, request.company(), request.position(),
                request.jdRaw(), now);
        if (!Objects.equals(target.getId(), record.getJobTargetId())) {
            record.setJobTargetId(target.getId());
        }

        if (!nextStatus.equals(record.getStatus())) {
            appendHistory(record, nextStatus, now);
        }
        record.setStatus(nextStatus);
        record.setDeliveryDate(request.deliveryDate());
        record.setExamTime(request.examTime());
        record.setInterviewTime(request.interviewTime());
        record.setDeadline(request.deadline());
        record.setSkillReq(blankToNull(request.skillReq()));
        record.setRemark(blankToNull(request.remark()));
        record.setUpdatedAt(now);
        deliveryMapper.updateById(record);
        return toData(record, latestInterviewRecord(record.getId()), true);
    }

    /** 逻辑删除是幂等操作；重复删除仍返回成功，不向调用方暴露内部行状态。 */
    public void delete(Long userId, Long id) {
        DeliveryRecord record = deliveryMapper.selectAnyById(id);
        if (record == null) {
            throw BizException.notFound("投递记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw ForbiddenException.forbidden();
        }
        if (Integer.valueOf(1).equals(record.getDeleted())) {
            return;
        }
        if (deliveryMapper.deleteById(id) > 0) {
            return;
        }
        /*
         * 并发删除可能让两个请求同时通过预读：后到请求影响 0 行。
         * 重新读取原始行确认结果，已删除时仍按成功 no-op 返回，避免误报 500。
         */
        DeliveryRecord latest = deliveryMapper.selectAnyById(id);
        if (latest == null) {
            throw BizException.notFound("投递记录不存在");
        }
        if (!latest.getUserId().equals(userId)) {
            throw ForbiddenException.forbidden();
        }
        if (!Integer.valueOf(1).equals(latest.getDeleted())) {
            throw new IllegalStateException("投递记录删除失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long userId, Long id, DeliveryStatusRequest request) {
        DeliveryRecord record = requireOwned(userId, id);
        String target = normalizeStatus(request.status(), false);
        if (target.equals(record.getStatus())) {
            return;
        }
        assertCanTransit(record.getStatus(), target);
        appendHistory(record, target, LocalDateTime.now(clock));
        record.setStatus(target);
        record.setUpdatedAt(LocalDateTime.now(clock));
        deliveryMapper.updateById(record);
    }

    /**
     * 同步解析 JD。这里使用可解释的本地规则提取字段，AI 调用用于记录治理日志与
     * 兼容真实模型接入；即使模型不可用，仍能返回可编辑的结构化结果。
     */
    public Map<String, Object> parseJobDescription(Long userId, ParseJobDescriptionRequest request) {
        String jd = request.jdRaw().trim();
        quotaService.deduct(userId, "jd_parse");
        String operationId = "jd_parse:" + UUID.randomUUID();
        try {
            aiGateway.chat(userId, "jd_parse",
                    "从以下 JD 提取公司、岗位、技能、亮点与截止日期：\n" + jd,
                    operationId);
        } catch (RuntimeException exception) {
            quotaService.refund(userId);
            log.warn("JD 解析失败，已退还配额 operationId={}", operationId, exception);
            throw BizException.business("JD解析失败，配额已退还");
        }

        String company = extractCompany(jd);
        String position = extractPosition(jd);
        String skills = String.join(",", extractSkills(jd));
        List<String> highlights = extractHighlights(jd);
        LocalDate deadline = extractDate(jd);
        LocalDateTime now = LocalDateTime.now(clock);
        JobTarget target = upsertTarget(userId, company, position, jd, now);
        target.setSkills(blankToNull(skills));
        target.setHighlights(jsonUtils.write(highlights));
        // deadline 是 AI 从 JD 解析出的原始值，只在本接口写入目标岗位。
        target.setDeadline(deadline);
        target.setUpdatedAt(now);
        jobTargetMapper.updateById(target);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobTargetId", target.getId());
        data.put("company", target.getCompany());
        data.put("position", target.getPosition());
        data.put("skills", target.getSkills());
        data.put("highlights", highlights);
        data.put("deadline", target.getDeadline());
        return data;
    }

    /**
     * 派生待办不落表：笔试/面试窗口为未来 24 小时，截止日期包含今天至今天后 3 天。
     */
    public List<Map<String, Object>> todo(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime end = now.plusHours(24);
        LocalDate today = now.toLocalDate();
        LocalDate deadlineEnd = today.plusDays(3);
        List<DeliveryRecord> records = deliveryMapper.selectList(
                new LambdaQueryWrapper<DeliveryRecord>()
                        .eq(DeliveryRecord::getUserId, userId)
                        .notIn(DeliveryRecord::getStatus, "offer", "rejected"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (DeliveryRecord record : records) {
            JobTarget target = jobTargetMapper.selectById(record.getJobTargetId());
            if (target == null) {
                continue;
            }
            if (inWindow(record.getExamTime(), now, end)) {
                result.add(todoItem("exam", record, target, record.getExamTime(), now));
            }
            if (inWindow(record.getInterviewTime(), now, end)) {
                result.add(todoItem("interview", record, target, record.getInterviewTime(), now));
            }
            if (record.getDeadline() != null
                    && !record.getDeadline().isBefore(today)
                    && !record.getDeadline().isAfter(deadlineEnd)) {
                result.add(todoItem("deadline", record, target,
                        record.getDeadline().atTime(LocalTime.of(23, 59, 59)), now));
            }
        }
        result.sort(Comparator
                .comparing((Map<String, Object> item) -> (LocalDateTime) item.get("sortTime"))
                .thenComparingInt(item -> switch (String.valueOf(item.get("type"))) {
                    case "exam" -> 1;
                    case "interview" -> 2;
                    default -> 3;
                })
                .thenComparingLong(item -> ((Number) item.get("recordId")).longValue()));
        result.forEach(item -> item.remove("sortTime"));
        return result;
    }

    public DeliveryRecord requireOwned(Long userId, Long id) {
        DeliveryRecord record = deliveryMapper.selectById(id);
        if (record == null) {
            throw BizException.notFound("投递记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw ForbiddenException.forbidden();
        }
        return record;
    }

    private JobTarget upsertTarget(Long userId, String company, String position,
                                   String jdRaw, LocalDateTime now) {
        String cleanCompany = trim(company);
        String cleanPosition = trim(position);
        if (cleanCompany.isBlank() || cleanPosition.isBlank()) {
            throw BizException.badRequest("公司名称和岗位名称不能为空");
        }
        JobTarget existing = jobTargetMapper.selectOne(
                new LambdaQueryWrapper<JobTarget>()
                        .eq(JobTarget::getUserId, userId)
                        .eq(JobTarget::getCompany, cleanCompany)
                        .eq(JobTarget::getPosition, cleanPosition)
                        .last("LIMIT 1"));
        if (existing != null) {
            String cleanJd = blankToNull(jdRaw);
            if (cleanJd != null && !cleanJd.equals(blankToNull(existing.getJdRaw()))) {
                existing.setJdRaw(cleanJd);
                // JD 原文已更换，旧的技能、亮点和解析截止时间不能再对应新原文。
                existing.setSkills(null);
                existing.setHighlights(null);
                existing.setDeadline(null);
            }
            existing.setUpdatedAt(now);
            jobTargetMapper.updateById(existing);
            return existing;
        }
        JobTarget target = new JobTarget();
        target.setUserId(userId);
        target.setCompany(cleanCompany);
        target.setPosition(cleanPosition);
        target.setJdRaw(blankToNull(jdRaw));
        target.setDeadline(null);
        target.setSkills(null);
        target.setHighlights(null);
        target.setCreatedAt(now);
        target.setUpdatedAt(now);
        try {
            jobTargetMapper.insert(target);
            return target;
        } catch (DuplicateKeyException exception) {
            // 并发首次投递可能同时创建岗位目标，唯一键冲突后回读即可保证幂等。
            JobTarget concurrent = jobTargetMapper.selectOne(
                    new LambdaQueryWrapper<JobTarget>()
                            .eq(JobTarget::getUserId, userId)
                            .eq(JobTarget::getCompany, cleanCompany)
                            .eq(JobTarget::getPosition, cleanPosition)
                            .last("LIMIT 1"));
            if (concurrent == null) {
                throw exception;
            }
            return concurrent;
        }
    }

    /**
     * 编辑投递时解析最终岗位目标。
     *
     * <p>公司或岗位变化时不能原地改写旧目标，否则同一旧目标下的其他投递会被串改。
     * 普通编辑只更新 JD 原文；skills/highlights/deadline 只由 JD 解析接口产生。</p>
     */
    private JobTarget resolveUpdateTarget(Long userId, JobTarget current,
                                          String company, String position,
                                          String jdRaw, LocalDateTime now) {
        String cleanCompany = trim(company);
        String cleanPosition = trim(position);
        if (cleanCompany.isBlank() || cleanPosition.isBlank()) {
            throw BizException.badRequest("公司名称和岗位名称不能为空");
        }

        boolean identityChanged = !cleanCompany.equals(current.getCompany())
                || !cleanPosition.equals(current.getPosition());
        JobTarget target = current;
        if (identityChanged) {
            target = jobTargetMapper.selectOne(new LambdaQueryWrapper<JobTarget>()
                    .eq(JobTarget::getUserId, userId)
                    .eq(JobTarget::getCompany, cleanCompany)
                    .eq(JobTarget::getPosition, cleanPosition)
                    .last("LIMIT 1"));
            if (target == null) {
                target = new JobTarget();
                target.setUserId(userId);
                target.setCompany(cleanCompany);
                target.setPosition(cleanPosition);
                target.setJdRaw(null);
                target.setSkills(null);
                target.setHighlights(null);
                target.setDeadline(null);
                target.setCreatedAt(now);
                target.setUpdatedAt(now);
                try {
                    jobTargetMapper.insert(target);
                } catch (DuplicateKeyException exception) {
                    target = jobTargetMapper.selectOne(new LambdaQueryWrapper<JobTarget>()
                            .eq(JobTarget::getUserId, userId)
                            .eq(JobTarget::getCompany, cleanCompany)
                            .eq(JobTarget::getPosition, cleanPosition)
                            .last("LIMIT 1"));
                    if (target == null) {
                        throw exception;
                    }
                }
            }
        }

        String newJd = blankToNull(jdRaw);
        boolean jdChanged = !Objects.equals(blankToNull(target.getJdRaw()), newJd);
        target.setCompany(cleanCompany);
        target.setPosition(cleanPosition);
        target.setJdRaw(newJd);
        if (jdChanged || newJd == null) {
            target.setSkills(null);
            target.setHighlights(null);
            target.setDeadline(null);
        }
        target.setUpdatedAt(now);
        jobTargetMapper.updateById(target);
        return target;
    }

    private Map<String, Object> toData(DeliveryRecord record, MockInterviewRecord interview,
                                       boolean includeJd) {
        JobTarget target = jobTargetMapper.selectById(record.getJobTargetId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", record.getId());
        data.put("company", target == null ? "" : target.getCompany());
        data.put("position", target == null ? "" : target.getPosition());
        data.put("jdRaw", includeJd && target != null ? target.getJdRaw() : null);
        data.put("deliveryDate", record.getDeliveryDate());
        data.put("status", record.getStatus());
        data.put("examTime", record.getExamTime());
        data.put("interviewTime", record.getInterviewTime());
        data.put("skillReq", record.getSkillReq());
        data.put("deadline", record.getDeadline());
        data.put("remark", record.getRemark());
        data.put("statusHistory", jsonUtils.readList(record.getStatusHistory()));
        data.put("interviewSummary", interview == null ? null : Map.of(
                "id", interview.getId(),
                "status", interview.getStatus(),
                "archived", Integer.valueOf(1).equals(interview.getArchived()),
                "progressIndex", interview.getProgressIndex() == null ? 0 : interview.getProgressIndex(),
                "totalQuestions", jsonUtils.readList(interview.getQuestionsJson()).size()
        ));
        data.put("createdAt", record.getCreatedAt());
        data.put("updatedAt", record.getUpdatedAt());
        return data;
    }

    private MockInterviewRecord latestInterviewRecord(Long deliveryId) {
        return interviewMapper.selectOne(new LambdaQueryWrapper<MockInterviewRecord>()
                .eq(MockInterviewRecord::getDeliveryId, deliveryId)
                .orderByDesc(MockInterviewRecord::getCreatedAt)
                .last("LIMIT 1"));
    }

    private BigDecimal conversionRate(List<DeliveryRecord> records) {
        long denominator = 0;
        long numerator = 0;
        for (DeliveryRecord record : records) {
            List<Map<String, Object>> history = jsonUtils.readList(record.getStatusHistory());
            boolean applied = history.stream().anyMatch(item ->
                    Set.of("applied", "exam", "interview", "offer")
                            .contains(String.valueOf(item.get("to"))));
            boolean interviewed = history.stream().anyMatch(item ->
                    Set.of("interview", "offer").contains(String.valueOf(item.get("to"))));
            if (applied) {
                denominator++;
            }
            if (interviewed) {
                numerator++;
            }
        }
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator * 100)
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private void assertCanTransit(String from, String to) {
        if (!STATUSES.contains(from) || !STATUSES.contains(to)) {
            throw BizException.badRequest("投递状态不合法");
        }
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw BizException.badRequest("不允许从" + from + "直接到" + to);
        }
    }

    private void appendHistory(DeliveryRecord record, String target, LocalDateTime now) {
        List<Map<String, Object>> history = new ArrayList<>(
                jsonUtils.readList(record.getStatusHistory()));
        history.add(historyItem(record.getStatus(), target, now));
        record.setStatusHistory(jsonUtils.write(history));
    }

    private Map<String, Object> historyItem(String from, String to, LocalDateTime now) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("from", from);
        item.put("to", to);
        item.put("changedAt", now);
        return item;
    }

    private String normalizeStatus(String status, boolean create) {
        if (status == null || status.isBlank()) {
            return create ? "pending" : "pending";
        }
        String cleaned = status.trim().toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(cleaned)) {
            throw BizException.badRequest("投递状态不合法");
        }
        return cleaned;
    }

    private String extractCompany(String jd) {
        Matcher matcher = Pattern.compile("(?:公司|企业)(?:名称)?[：:]\\s*([^\\n，,；;]{1,100})").matcher(jd);
        if (matcher.find()) {
            return trim(matcher.group(1));
        }
        String first = jd.lines().findFirst().orElse("目标公司");
        return trim(first.length() > 100 ? first.substring(0, 100) : first);
    }

    private String extractPosition(String jd) {
        Matcher matcher = Pattern.compile("(?:岗位|职位)(?:名称)?[：:]\\s*([^\\n，,；;]{1,100})").matcher(jd);
        if (matcher.find()) {
            return trim(matcher.group(1));
        }
        return "Java 后端开发";
    }

    private List<String> extractSkills(String jd) {
        String lower = jd.toLowerCase(Locale.ROOT);
        return KNOWN_SKILLS.stream()
                .filter(skill -> lower.contains(skill.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private List<String> extractHighlights(String jd) {
        return jd.lines()
                .map(String::trim)
                .filter(line -> line.length() >= 6)
                .filter(line -> !line.startsWith("岗位职责") && !line.startsWith("任职要求"))
                .limit(5)
                .toList();
    }

    private LocalDate extractDate(String jd) {
        Matcher matcher = DATE_PATTERN.matcher(jd);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean inWindow(LocalDateTime time, LocalDateTime start, LocalDateTime end) {
        return time != null && !time.isBefore(start) && !time.isAfter(end);
    }

    private Map<String, Object> todoItem(String type, DeliveryRecord record,
                                         JobTarget target, LocalDateTime time,
                                         LocalDateTime now) {
        long remaining = Math.max(0, Duration.between(now, time).getSeconds());
        String displayStatus;
        if (remaining > 0) {
            displayStatus = "upcoming";
        } else if ("deadline".equals(type)) {
            displayStatus = "expired";
        } else {
            displayStatus = "started";
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("recordId", record.getId());
        item.put("company", target.getCompany());
        item.put("position", target.getPosition());
        item.put("time", time);
        item.put("remainingSeconds", remaining);
        item.put("displayStatus", displayStatus);
        // 仅供服务端排序，返回前会移除，避免污染冻结的 wire 字段集。
        item.put("sortTime", time);
        return item;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
