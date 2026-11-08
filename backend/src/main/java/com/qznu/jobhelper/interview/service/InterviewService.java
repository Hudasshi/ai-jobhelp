package com.qznu.jobhelper.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qznu.jobhelper.ability.entity.AbilityEntry;
import com.qznu.jobhelper.ability.mapper.AbilityEntryMapper;
import com.qznu.jobhelper.common.exception.BizException;
import com.qznu.jobhelper.common.exception.ForbiddenException;
import com.qznu.jobhelper.common.util.JsonUtils;
import com.qznu.jobhelper.delivery.entity.DeliveryRecord;
import com.qznu.jobhelper.delivery.entity.JobTarget;
import com.qznu.jobhelper.delivery.mapper.DeliveryRecordMapper;
import com.qznu.jobhelper.delivery.mapper.JobTargetMapper;
import com.qznu.jobhelper.interview.dto.InterviewAnswerRequest;
import com.qznu.jobhelper.interview.dto.StartInterviewRequest;
import com.qznu.jobhelper.interview.entity.MockInterviewRecord;
import com.qznu.jobhelper.interview.mapper.MockInterviewRecordMapper;
import com.qznu.jobhelper.support.quota.QuotaService;
import com.qznu.jobhelper.task.AiGateway;
import com.qznu.jobhelper.task.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟面试服务。
 *
 * <p>出题走异步任务，逐题点评是同步 AI 调用。点评使用 requestId 做业务幂等：
 * 同一次提交因网络重试再次到达时，直接回放已保存的结果，不再扣配额。</p>
 */
@Slf4j
@Service
public class InterviewService {

    private static final int KEEP_DAYS = 180;
    private static final Map<Long, Object> INTERVIEW_LOCKS = new ConcurrentHashMap<>();

    private final MockInterviewRecordMapper interviewMapper;
    private final DeliveryRecordMapper deliveryMapper;
    private final JobTargetMapper jobTargetMapper;
    private final AbilityEntryMapper abilityMapper;
    private final TaskService taskService;
    private final QuotaService quotaService;
    private final AiGateway aiGateway;
    private final JsonUtils jsonUtils;
    private final Clock clock;

    public InterviewService(MockInterviewRecordMapper interviewMapper,
                            DeliveryRecordMapper deliveryMapper,
                            JobTargetMapper jobTargetMapper,
                            AbilityEntryMapper abilityMapper,
                            TaskService taskService,
                            QuotaService quotaService,
                            AiGateway aiGateway,
                            JsonUtils jsonUtils,
                            Clock clock) {
        this.interviewMapper = interviewMapper;
        this.deliveryMapper = deliveryMapper;
        this.jobTargetMapper = jobTargetMapper;
        this.abilityMapper = abilityMapper;
        this.taskService = taskService;
        this.quotaService = quotaService;
        this.aiGateway = aiGateway;
        this.jsonUtils = jsonUtils;
        this.clock = clock;
    }

    /**
     * 发起面试前要求能力库存在已确认且启用的条目，避免模型用空上下文凭空出题。
     */
    public Map<String, Object> start(Long userId, StartInterviewRequest request) {
        DeliveryRecord delivery = deliveryMapper.selectById(request.deliveryId());
        if (delivery == null) {
            throw BizException.notFound("投递记录不存在");
        }
        if (!delivery.getUserId().equals(userId)) {
            throw ForbiddenException.forbidden();
        }
        JobTarget target = jobTargetMapper.selectById(delivery.getJobTargetId());
        if (target == null) {
            throw BizException.notFound("岗位目标不存在");
        }
        long abilityCount = abilityMapper.selectCount(new LambdaQueryWrapper<AbilityEntry>()
                .eq(AbilityEntry::getUserId, userId)
                .eq(AbilityEntry::getStatus, "confirmed")
                .eq(AbilityEntry::getEnabled, 1));
        if (abilityCount == 0) {
            throw BizException.business("请先在能力库中确认至少一条经历");
        }

        String focus = request.focus() == null || request.focus().isBlank()
                ? "comprehensive" : request.focus();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("deliveryId", delivery.getId());
        snapshot.put("jobTargetId", target.getId());
        snapshot.put("company", target.getCompany());
        snapshot.put("position", target.getPosition());
        snapshot.put("jd", target.getJdRaw() == null ? "" : target.getJdRaw());
        snapshot.put("focus", focus);
        snapshot.put("abilityCount", abilityCount);
        Long taskId = taskService.submit(userId, "interview_gen", delivery.getId(), snapshot);
        return Map.of("taskId", taskId);
    }

    public Map<String, Object> get(Long userId, Long id) {
        MockInterviewRecord record = requireOwned(userId, id);
        List<Map<String, Object>> questions = normalizeQuestions(
                jsonUtils.readList(record.getQuestionsJson()));
        List<Map<String, Object>> answers = joinAnswers(
                jsonUtils.readList(record.getAnswersJson()),
                jsonUtils.readList(record.getFeedbackJson()));
        boolean expired = record.getCreatedAt() != null
                && record.getCreatedAt().isBefore(LocalDateTime.now(clock).minusDays(KEEP_DAYS));
        boolean contentEmpty = questions.isEmpty() && answers.isEmpty()
                && (record.getReviewText() == null || record.getReviewText().isBlank());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", record.getId());
        data.put("deliveryId", record.getDeliveryId());
        data.put("status", record.getStatus());
        data.put("questions", questions);
        data.put("answers", answers);
        data.put("progressIndex", record.getProgressIndex() == null ? 0 : record.getProgressIndex());
        data.put("reviewText", record.getReviewText());
        data.put("archived", Integer.valueOf(1).equals(record.getArchived()));
        data.put("archiveTime", record.getArchiveTime());
        data.put("contentExpired", expired && contentEmpty);
        data.put("createdAt", record.getCreatedAt());
        data.put("updatedAt", record.getUpdatedAt());
        return data;
    }

    public Map<String, Object> list(Long userId, long page, long size) {
        LambdaQueryWrapper<MockInterviewRecord> query =
                new LambdaQueryWrapper<MockInterviewRecord>()
                        .eq(MockInterviewRecord::getUserId, userId)
                        .orderByDesc(MockInterviewRecord::getCreatedAt)
                        .orderByDesc(MockInterviewRecord::getId);
        long total = interviewMapper.selectCount(query);
        query.last("LIMIT " + size + " OFFSET " + ((page - 1) * size));
        List<Map<String, Object>> records = interviewMapper.selectList(query).stream()
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
     * 提交答案并生成点评。加锁只覆盖单场面试的同步处理，保证同一面试不会因双击
     * 同时推进进度；不同面试之间仍然可以并行。
     */
    public Map<String, Object> answer(Long userId, Long id, InterviewAnswerRequest request) {
        Object lock = INTERVIEW_LOCKS.computeIfAbsent(id, ignored -> new Object());
        synchronized (lock) {
            return answerLocked(userId, id, request);
        }
    }

    private Map<String, Object> answerLocked(Long userId, Long id,
                                             InterviewAnswerRequest request) {
        MockInterviewRecord record = requireOwned(userId, id);
        List<Map<String, Object>> questions = normalizeQuestions(
                jsonUtils.readList(record.getQuestionsJson()));
        if (request.index() >= questions.size()) {
            throw BizException.badRequest("题号超出范围");
        }
        List<Map<String, Object>> logs = new ArrayList<>(
                jsonUtils.readList(record.getRequestLogJson()));
        Map<String, Object> existing = logs.stream()
                .filter(item -> request.requestId().equals(item.get("requestId")))
                .findFirst()
                .orElse(null);
        String digest = digest(request.index() + "\n" + request.answer().trim());
        if (existing != null) {
            if (!digest.equals(existing.get("payloadDigest"))) {
                throw BizException.conflict("请求正在处理或 requestId 冲突");
            }
            Object result = existing.get("resultRef");
            if (result instanceof Map<?, ?> map && existing.get("completedAt") != null) {
                return stringKeyMap(map);
            }
            throw BizException.conflict("请求正在处理或 requestId 冲突");
        }
        if (Integer.valueOf(1).equals(record.getArchived())
                || "reviewed".equals(record.getStatus())) {
            throw BizException.badRequest("已复盘或已归档的面试不能继续作答");
        }
        int progress = record.getProgressIndex() == null ? 0 : record.getProgressIndex();
        if (request.index() > progress) {
            throw BizException.badRequest("请按顺序完成题目");
        }

        String operationId = "interview:" + id + ":" + request.requestId();
        Map<String, Object> startedLog = new LinkedHashMap<>();
        startedLog.put("requestId", request.requestId());
        startedLog.put("payloadDigest", digest);
        startedLog.put("resultIndex", request.index());
        startedLog.put("quotaOperationId", operationId);
        startedLog.put("startedAt", LocalDateTime.now(clock));
        logs.add(startedLog);

        quotaService.deduct(userId, operationId);
        String aiText;
        try {
            Map<String, Object> question = questions.get(request.index());
            aiText = aiGateway.chat(userId, "interview_feedback",
                    "请点评这道面试题的回答。题目：" + question.get("question")
                            + "\n分类：" + question.get("category")
                            + "\n回答：" + request.answer(),
                    operationId).text();
        } catch (RuntimeException exception) {
            quotaService.refund(userId);
            startedLog.put("failedAt", LocalDateTime.now(clock));
            startedLog.put("failCode", "ai_failed");
            record.setRequestLogJson(jsonUtils.write(logs));
            interviewMapper.updateById(record);
            throw BizException.business("点评失败，配额已退还");
        }

        Map<String, Object> feedback = buildFeedback(request.answer(), aiText);
        int nextProgress = request.index() == progress ? progress + 1 : progress;
        List<Map<String, Object>> answers = upsertByIndex(
                jsonUtils.readList(record.getAnswersJson()), request.index(), request.answer());
        List<Map<String, Object>> feedbacks = upsertByIndex(
                jsonUtils.readList(record.getFeedbackJson()), request.index(), feedback);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("index", request.index());
        result.put("answer", request.answer());
        result.put("feedback", feedback);
        result.put("progressIndex", nextProgress);

        startedLog.put("resultRef", result);
        startedLog.put("completedAt", LocalDateTime.now(clock));
        record.setAnswersJson(jsonUtils.write(answers));
        record.setFeedbackJson(jsonUtils.write(feedbacks));
        record.setProgressIndex(nextProgress);
        record.setStatus("answering");
        record.setRequestLogJson(jsonUtils.write(logs));
        record.setUpdatedAt(LocalDateTime.now(clock));
        interviewMapper.updateById(record);

        // 如果在点评过程中用户已经生成过复盘，答案变化后同步刷新模板复盘。
        if (record.getReviewText() != null && !record.getReviewText().isBlank()) {
            record.setReviewText(composeReview(record));
            interviewMapper.updateById(record);
        }
        return result;
    }

    /** 复盘完全由服务端模板拼接，不调用 AI，也不消耗配额。 */
    public Map<String, Object> review(Long userId, Long id) {
        MockInterviewRecord record = requireOwned(userId, id);
        String reviewText = composeReview(record);
        record.setReviewText(reviewText);
        if (!Integer.valueOf(1).equals(record.getArchived())) {
            record.setStatus("reviewed");
        }
        record.setUpdatedAt(LocalDateTime.now(clock));
        interviewMapper.updateById(record);
        return Map.of("reviewText", reviewText);
    }

    public void archive(Long userId, Long id) {
        MockInterviewRecord record = requireOwned(userId, id);
        if (Integer.valueOf(1).equals(record.getArchived())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        record.setArchived(1);
        record.setArchiveTime(now);
        record.setStatus("archived");
        record.setUpdatedAt(now);
        interviewMapper.updateById(record);
    }

    private MockInterviewRecord requireOwned(Long userId, Long id) {
        MockInterviewRecord record = interviewMapper.selectById(id);
        if (record == null) {
            throw BizException.notFound("面试记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw ForbiddenException.forbidden();
        }
        return record;
    }

    /**
     * TaskService 历史数据和演示种子使用 no/中文分类；这里统一投影成 OpenAPI
     * 规定的 index/category 枚举，避免前端需要兼容两套格式。
     */
    private List<Map<String, Object>> normalizeQuestions(List<Map<String, Object>> raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Map<String, Object> source = raw.get(i);
            int index = number(source.get("index"), number(source.get("no"), i + 1) - 1);
            String category = switch (String.valueOf(source.get("category"))) {
                case "深挖", "technical_deep_dive" -> "technical_deep_dive";
                case "八股", "fundamentals" -> "fundamentals";
                default -> "behavioral";
            };
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", index);
            item.put("category", category);
            item.put("question", String.valueOf(source.getOrDefault("question", "")));
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> joinAnswers(List<Map<String, Object>> answers,
                                                  List<Map<String, Object>> feedbacks) {
        Map<Integer, Map<String, Object>> feedbackByIndex = new LinkedHashMap<>();
        for (Map<String, Object> feedback : feedbacks) {
            feedbackByIndex.put(indexOf(feedback), feedback);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> answer : answers) {
            int index = indexOf(answer);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", index);
            item.put("answer", String.valueOf(answer.getOrDefault("answer", "")));
            Map<String, Object> feedback = feedbackByIndex.get(index);
            item.put("feedback", feedback == null ? defaultFeedback() : feedback);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> upsertByIndex(List<Map<String, Object>> source,
                                                    int index, Object value) {
        List<Map<String, Object>> result = new ArrayList<>(source);
        result.removeIf(item -> indexOf(item) == index);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("index", index);
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, content) -> row.put(String.valueOf(key), content));
        } else {
            row.put("answer", value);
            row.put("answeredAt", LocalDateTime.now(clock));
        }
        result.add(row);
        result.sort((left, right) -> Integer.compare(indexOf(left), indexOf(right)));
        return result;
    }

    /**
     * 本地点评规则让无 API Key 的课堂环境也有稳定演示结果，同时真实 AI 返回的
     * 建议会作为示范答案的一部分保留，做到可降级而不改变接口形状。
     */
    private Map<String, Object> buildFeedback(String answer, String aiText) {
        int length = answer == null ? 0 : answer.trim().length();
        boolean quantified = answer != null && answer.matches(".*\\d+.*");
        String good = length >= 60
                ? "回答覆盖了问题核心，并给出了较完整的背景与处理过程。"
                : "回答方向正确，能够直接回应问题。";
        String bad;
        if (length == 0) {
            bad = "答案为空，无法判断你的真实思路。";
        } else if (length < 60) {
            bad = "细节不足，建议补充场景、方案选择与最终结果。";
        } else if (!quantified) {
            bad = "缺少可量化结果，招聘方难以判断方案效果。";
        } else {
            bad = "可以进一步说明方案取舍和边界条件，体现系统性思考。";
        }
        String sample = aiText == null || aiText.isBlank()
                ? "建议按“背景—目标—方案—取舍—结果”组织答案，并补充关键指标。"
                : aiText;
        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("good", good);
        feedback.put("bad", bad);
        feedback.put("sample", sample);
        return feedback;
    }

    private Map<String, Object> defaultFeedback() {
        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("good", "");
        feedback.put("bad", "");
        feedback.put("sample", "");
        return feedback;
    }

    private String composeReview(MockInterviewRecord record) {
        List<Map<String, Object>> questions = normalizeQuestions(
                jsonUtils.readList(record.getQuestionsJson()));
        List<Map<String, Object>> answers = joinAnswers(
                jsonUtils.readList(record.getAnswersJson()),
                jsonUtils.readList(record.getFeedbackJson()));
        StringBuilder text = new StringBuilder("模拟面试复盘\n");
        text.append("完成题目：").append(answers.size()).append("/")
                .append(questions.size()).append("\n\n");
        if (answers.isEmpty()) {
            text.append("本场尚未作答，建议至少完成一题后再复盘。\n");
            return text.toString();
        }
        for (Map<String, Object> answer : answers) {
            int index = indexOf(answer);
            if (index < 0 || index >= questions.size()) {
                continue;
            }
            Map<String, Object> feedback = answer.get("feedback") instanceof Map<?, ?> map
                    ? stringKeyMap(map) : defaultFeedback();
            text.append("第").append(index + 1).append("题：")
                    .append(questions.get(index).get("question")).append("\n")
                    .append("你的回答：").append(answer.get("answer")).append("\n")
                    .append("优点：").append(feedback.get("good")).append("\n")
                    .append("改进：").append(feedback.get("bad")).append("\n")
                    .append("建议表达：").append(feedback.get("sample")).append("\n\n");
        }
        text.append("下一步建议：用 STAR 结构重写薄弱题目，并补充量化结果与方案取舍。");
        return text.toString();
    }

    private Map<String, Object> toSummary(MockInterviewRecord record) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", record.getId());
        data.put("deliveryId", record.getDeliveryId());
        data.put("status", record.getStatus());
        data.put("progressIndex", record.getProgressIndex() == null ? 0 : record.getProgressIndex());
        data.put("totalQuestions", jsonUtils.readList(record.getQuestionsJson()).size());
        data.put("archived", Integer.valueOf(1).equals(record.getArchived()));
        data.put("createdAt", record.getCreatedAt());
        return data;
    }

    private int indexOf(Map<String, Object> item) {
        return number(item.get("index"), number(item.get("no"), 1) - 1);
    }

    private int number(Object primary, int fallback) {
        return primary instanceof Number number ? number.intValue() : fallback;
    }

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成请求摘要", exception);
        }
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
