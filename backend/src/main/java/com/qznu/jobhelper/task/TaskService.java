package com.qznu.jobhelper.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qznu.jobhelper.ability.entity.AbilityEntry;
import com.qznu.jobhelper.ability.mapper.AbilityEntryMapper;
import com.qznu.jobhelper.common.exception.BizException;
import com.qznu.jobhelper.common.exception.ForbiddenException;
import com.qznu.jobhelper.common.util.JsonUtils;
import com.qznu.jobhelper.diagnosis.entity.ResumeDiagnoseRecord;
import com.qznu.jobhelper.diagnosis.mapper.ResumeDiagnoseRecordMapper;
import com.qznu.jobhelper.interview.entity.MockInterviewRecord;
import com.qznu.jobhelper.interview.mapper.MockInterviewRecordMapper;
import com.qznu.jobhelper.resume.entity.UserResume;
import com.qznu.jobhelper.resume.mapper.UserResumeMapper;
import com.qznu.jobhelper.support.config.ConfigService;
import com.qznu.jobhelper.support.file.FileStorage;
import com.qznu.jobhelper.support.file.TextExtractor;
import com.qznu.jobhelper.support.quota.QuotaService;
import com.qznu.jobhelper.task.entity.AiTask;
import com.qznu.jobhelper.task.mapper.AiTaskMapper;
import com.qznu.jobhelper.user.entity.User;
import com.qznu.jobhelper.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 异步任务编排与执行。
 *
 * <p>这里集中处理四类任务，避免业务模块各自维护一套状态机。
 * 任务创建时扣一次配额，失败时只退款一次；执行线程不使用 Web ThreadLocal。</p>
 */
@Slf4j
@Service
public class TaskService {

    private static final int RESULT_KEEP_DAYS = 180;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final List<String> KNOWN_SKILLS = List.of(
            "Java", "Spring Boot", "Spring Cloud", "MyBatis", "MySQL", "Redis",
            "Kafka", "RocketMQ", "RabbitMQ", "Go", "Python", "Vue", "JavaScript",
            "Docker", "Kubernetes", "Linux", "JVM", "JUC", "Netty", "ZooKeeper",
            "分布式", "高并发", "微服务", "幂等", "索引", "事务"
    );

    private final AiTaskMapper taskMapper;
    private final UserResumeMapper resumeMapper;
    private final AbilityEntryMapper abilityMapper;
    private final ResumeDiagnoseRecordMapper diagnoseMapper;
    private final MockInterviewRecordMapper interviewMapper;
    private final UserMapper userMapper;
    private final QuotaService quotaService;
    private final ConfigService configService;
    private final FileStorage fileStorage;
    private final TextExtractor textExtractor;
    private final AiGateway aiGateway;
    private final JsonUtils jsonUtils;
    private final Clock clock;
    private final Executor executor;

    public TaskService(AiTaskMapper taskMapper,
                       UserResumeMapper resumeMapper,
                       AbilityEntryMapper abilityMapper,
                       ResumeDiagnoseRecordMapper diagnoseMapper,
                       MockInterviewRecordMapper interviewMapper,
                       UserMapper userMapper,
                       QuotaService quotaService,
                       ConfigService configService,
                       FileStorage fileStorage,
                       TextExtractor textExtractor,
                       AiGateway aiGateway,
                       JsonUtils jsonUtils,
                       Clock clock,
                       @Qualifier("taskExecutor") Executor executor) {
        this.taskMapper = taskMapper;
        this.resumeMapper = resumeMapper;
        this.abilityMapper = abilityMapper;
        this.diagnoseMapper = diagnoseMapper;
        this.interviewMapper = interviewMapper;
        this.userMapper = userMapper;
        this.quotaService = quotaService;
        this.configService = configService;
        this.fileStorage = fileStorage;
        this.textExtractor = textExtractor;
        this.aiGateway = aiGateway;
        this.jsonUtils = jsonUtils;
        this.clock = clock;
        this.executor = executor;
    }

    /**
     * 创建排队任务并立即返回 taskId。
     */
    public Long submit(Long userId, String taskType, Long bizId, Map<String, Object> snapshot) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw ForbiddenException.accountInvalid();
        }

        quotaService.deduct(userId, taskType);
        LocalDateTime now = LocalDateTime.now(clock);
        AiTask task = new AiTask();
        task.setUserId(userId);
        task.setTaskType(taskType);
        task.setBizId(bizId);
        task.setStatus("QUEUED");
        task.setProgress(0);
        task.setInputSnapshot(jsonUtils.write(snapshot));
        task.setQuotaDate(quotaService.today());
        task.setQuotaGeneration(user.getQuotaGeneration());
        task.setQuotaState("charged");
        task.setRefunded(0);
        task.setReadFlag(0);
        task.setCreatedAt(now);
        task.setTimeoutAt(now.plusMinutes(configService.getInt("ai.task.timeout-min")));

        try {
            taskMapper.insert(task);
        } catch (RuntimeException exception) {
            quotaService.refund(userId);
            throw exception;
        }

        executor.execute(() -> execute(task.getId()));
        return task.getId();
    }

    public Map<String, Object> getTask(Long userId, Long taskId) {
        AiTask task = requireOwnedTask(userId, taskId);
        return toTaskData(task);
    }

    public void markRead(Long userId, Long taskId) {
        AiTask task = requireOwnedTask(userId, taskId);
        task.setReadFlag(1);
        taskMapper.updateById(task);
    }

    public AiTask requireOwnedTask(Long userId, Long taskId) {
        AiTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw BizException.notFound("任务不存在");
        }
        return task;
    }

    /**
     * 异步执行入口。所有异常都在这里收口为 FAIL，并触发退款。
     */
    private void execute(Long taskId) {
        AiTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        LocalDateTime started = LocalDateTime.now(clock);
        task.setStatus("RUNNING");
        task.setProgress(10);
        task.setStartedAt(started);
        taskMapper.updateById(task);

        try {
            TaskResult result = switch (task.getTaskType()) {
                case "resume_parse" -> executeResumeParse(task);
                case "resume_generate" -> executeResumeGenerate(task);
                case "diagnose" -> executeDiagnosis(task);
                case "interview_gen" -> executeInterviewGen(task);
                default -> throw new IllegalStateException("未知任务类型: " + task.getTaskType());
            };

            task.setStatus("SUCCESS");
            task.setProgress(100);
            task.setResultRef(result.resultRef());
            task.setResultJson(result.resultJson());
            task.setQuotaState("settled");
            task.setFinishedAt(LocalDateTime.now(clock));
            taskMapper.updateById(task);
        } catch (Exception exception) {
            log.error("AI 任务失败 taskId={}", taskId, exception);
            task.setStatus("FAIL");
            task.setFailReason(limit(safeMessage(exception), 500));
            task.setFinishedAt(LocalDateTime.now(clock));
            if (!"refunded".equals(task.getQuotaState())) {
                quotaService.refund(task.getUserId());
                task.setQuotaState("refunded");
                task.setRefunded(1);
            }
            taskMapper.updateById(task);
        }
    }

    /**
     * 从上传文件中抽取文本，再按简历结构启发式拆分待确认条目。
     */
    private TaskResult executeResumeParse(AiTask task) {
        Map<String, Object> input = jsonUtils.readMap(task.getInputSnapshot());
        Long resumeId = toLong(input.get("resumeId"));
        String filePath = String.valueOf(input.getOrDefault("filePath", ""));
        String extension = String.valueOf(input.getOrDefault("extension", "txt"));
        UserResume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BizExceptionFactory("简历版本不存在").build();
        }

        Path path = fileStorage.resolve(filePath);
        String text = textExtractor.extract(path, extension);
        if (text.isBlank()) {
            throw new BizExceptionFactory("简历文本为空，无法解析").build();
        }

        task.setProgress(45);
        taskMapper.updateById(task);
        List<ParsedEntry> parsed = parseEntries(text);
        int inserted = 0;
        int skipped = 0;
        for (ParsedEntry candidate : parsed) {
            long exists = abilityMapper.selectCount(new LambdaQueryWrapper<AbilityEntry>()
                    .eq(AbilityEntry::getUserId, task.getUserId())
                    .eq(AbilityEntry::getTitle, candidate.title()));
            if (exists > 0) {
                skipped++;
                continue;
            }
            LocalDateTime now = LocalDateTime.now(clock);
            AbilityEntry entry = new AbilityEntry();
            entry.setUserId(task.getUserId());
            entry.setEntryType(candidate.entryType());
            entry.setTitle(limit(candidate.title(), 500));
            entry.setDetailJson(jsonUtils.write(Map.of(
                    "description", candidate.text(),
                    "sourceText", limit(candidate.text(), 2000)
            )));
            entry.setSource("ai");
            entry.setStatus("pending");
            entry.setEnabled(1);
            entry.setSortNo(abilityMapper.nextSortNo(task.getUserId()));
            entry.setSourceResumeId(resumeId);
            entry.setDeleted(0);
            entry.setCreatedAt(now);
            entry.setUpdatedAt(now);
            abilityMapper.insert(entry);
            inserted++;
        }

        aiGateway.chat(task.getUserId(), "resume_parse",
                "解析简历文本，提取结构化能力条目：" + limit(text, 3000),
                String.valueOf(task.getId()));

        resume.setStatus("ready");
        resume.setRawText(limit(text, 60000));
        resume.setUpdatedAt(LocalDateTime.now(clock));
        resumeMapper.updateById(resume);
        task.setBizId(resumeId);
        task.setProgress(80);
        taskMapper.updateById(task);
        return new TaskResult(resumeId, jsonUtils.write(Map.of(
                "inserted", inserted,
                "skipped", skipped
        )));
    }

    /**
     * 按固定顺序组装草稿。草稿不直接写入 user_resume，用户确认后另存版本。
     */
    private TaskResult executeResumeGenerate(AiTask task) {
        Map<String, Object> input = jsonUtils.readMap(task.getInputSnapshot());
        List<Map<String, Object>> entries = castList(input.get("entries"));
        if (entries.isEmpty()) {
            throw new BizExceptionFactory("没有可用于生成简历的条目").build();
        }

        List<Map<String, Object>> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt(item -> typeOrder(String.valueOf(item.get("entryType")))));
        List<Map<String, Object>> items = new ArrayList<>();
        AtomicInteger order = new AtomicInteger(1);
        for (Map<String, Object> row : sorted) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entryId", toLong(row.get("entryId")));
            item.put("order", order.getAndIncrement());
            item.put("title", String.valueOf(row.getOrDefault("title", "")));
            item.put("text", String.valueOf(row.getOrDefault("text", "")));
            items.add(item);
        }
        String nickname = String.valueOf(input.getOrDefault("nickname", "个人简历"));
        String target = String.valueOf(input.getOrDefault("targetPosition", ""));
        String contentMd = buildResumeMarkdown(nickname, target, items);

        aiGateway.chat(task.getUserId(), "resume_generate",
                "根据岗位优化简历：" + contentMd, String.valueOf(task.getId()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("draftTaskId", task.getId());
        result.put("items", items);
        result.put("contentMd", contentMd);
        task.setProgress(80);
        taskMapper.updateById(task);
        return new TaskResult(null, jsonUtils.write(result));
    }

    /**
     * 生成可复算的四维诊断报告。权重固定：技能35%、项目30%、教育20%、表达15%。
     */
    private TaskResult executeDiagnosis(AiTask task) {
        Map<String, Object> input = jsonUtils.readMap(task.getInputSnapshot());
        String jd = String.valueOf(input.getOrDefault("jd", ""));
        List<Map<String, Object>> sourceSnapshot = castList(input.get("sourceSnapshot"));
        if (sourceSnapshot.isEmpty()) {
            throw new BizExceptionFactory("简历内容为空，无法诊断").build();
        }

        List<String> jdSkills = extractSkills(jd);
        int skillScore = scoreSkills(sourceSnapshot, jdSkills);
        int projectScore = scoreProjects(sourceSnapshot);
        int educationScore = scoreEducation(sourceSnapshot);
        int expressionScore = scoreExpression(sourceSnapshot);
        int total = BigDecimal.valueOf(skillScore * 0.35 + projectScore * 0.30
                        + educationScore * 0.20 + expressionScore * 0.15)
                .setScale(0, RoundingMode.HALF_UP).intValue();

        List<Map<String, Object>> dims = List.of(
                dim("skill", "技能匹配", 0.35, skillScore),
                dim("project", "项目经历", 0.30, projectScore),
                dim("education", "教育背景", 0.20, educationScore),
                dim("expression", "表达质量", 0.15, expressionScore)
        );
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : sourceSnapshot) {
            String original = String.valueOf(row.getOrDefault("text", ""));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entryId", toLong(row.get("entryId")));
            item.put("original", original);
            item.put("rewrite", rewrite(original));
            item.put("problem", original.matches(".*\\d+.*")
                    ? "职责描述较完整，但结果与职责的层次还可以更清晰。"
                    : "缺少可量化结果，招聘方难以判断贡献大小。");
            item.put("suggestion", "建议采用“动作 + 技术方案 + 量化结果”的句式。");
            item.put("verdict", "rewritten");
            item.put("adopted", false);
            items.add(item);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("score", total);
        report.put("dims", dims);
        report.put("items", items);
        report.put("skipped", List.of());
        report.put("sourceSnapshot", sourceSnapshot);

        LocalDateTime now = LocalDateTime.now(clock);
        ResumeDiagnoseRecord record = new ResumeDiagnoseRecord();
        record.setUserId(task.getUserId());
        record.setUsernameSnapshot(String.valueOf(input.getOrDefault("usernameSnapshot", "")));
        record.setPositionSnapshot(String.valueOf(input.getOrDefault("positionSnapshot", "")));
        record.setResumeId(toLong(input.get("resumeId")));
        record.setJobTargetId(nullableLong(input.get("jobTargetId")));
        record.setDeliveryId(nullableLong(input.get("deliveryId")));
        record.setTaskId(task.getId());
        record.setScore(total);
        record.setReportJson(jsonUtils.write(report));
        record.setStatus("success");
        record.setCostSeconds(Math.max(1, (int) java.time.Duration.between(
                task.getStartedAt(), now).toSeconds()));
        record.setTokens(estimateTokens(jd) + sourceSnapshot.size() * 120);
        record.setCreatedAt(now);
        diagnoseMapper.insert(record);

        aiGateway.chat(task.getUserId(), "diagnose",
                "诊断简历与 JD 匹配度：" + jd + "\n" + jsonUtils.write(sourceSnapshot),
                String.valueOf(task.getId()));
        return new TaskResult(record.getId(), null);
    }

    /**
     * 根据岗位技能生成 5/6 道面试题，结果在任务成功后可直接轮询获取。
     */
    private TaskResult executeInterviewGen(AiTask task) {
        Map<String, Object> input = jsonUtils.readMap(task.getInputSnapshot());
        String jd = String.valueOf(input.getOrDefault("jd", ""));
        List<String> skills = extractSkills(jd);
        if (skills.isEmpty()) {
            skills = List.of("Java", "MySQL", "项目经历");
        }
        List<Map<String, Object>> questions = new ArrayList<>();
        int count = jd.length() >= 50 ? 6 : 5;
        List<String> categories = List.of("深挖", "八股", "行为");
        for (int i = 0; i < count; i++) {
            String skill = skills.get(i % skills.size());
            String category = categories.get(i % categories.size());
            String question = switch (category) {
                case "深挖" -> "请结合项目说明你在 " + skill + " 方面做过的最有挑战的一次优化。";
                case "八股" -> "请解释 " + skill + " 的核心原理，以及在生产环境中常见的坑。";
                default -> "讲一次你在团队协作中推动技术方案落地的经历，你承担了什么角色？";
            };
            questions.add(Map.of(
                    "no", i + 1,
                    "category", category,
                    "question", question
            ));
        }

        LocalDateTime now = LocalDateTime.now(clock);
        MockInterviewRecord record = new MockInterviewRecord();
        record.setUserId(task.getUserId());
        record.setDeliveryId(toLong(input.get("deliveryId")));
        record.setJobTargetId(toLong(input.get("jobTargetId")));
        record.setQuestionsJson(jsonUtils.write(questions));
        record.setAnswersJson("[]");
        record.setFeedbackJson("[]");
        record.setRequestLogJson("[]");
        record.setProgressIndex(0);
        record.setArchived(0);
        record.setStatus("created");
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        interviewMapper.insert(record);

        aiGateway.chat(task.getUserId(), "interview_gen",
                "根据岗位生成模拟面试题：" + jd, String.valueOf(task.getId()));
        return new TaskResult(record.getId(), null);
    }

    private Map<String, Object> toTaskData(AiTask task) {
        boolean expired = task.getCreatedAt() != null
                && task.getCreatedAt().isBefore(LocalDateTime.now(clock).minusDays(RESULT_KEEP_DAYS));
        Object resultData = null;
        if (!expired && "SUCCESS".equals(task.getStatus()) && task.getResultJson() != null) {
            resultData = jsonUtils.readMap(task.getResultJson());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getId());
        data.put("taskType", task.getTaskType());
        data.put("status", task.getStatus());
        data.put("progress", task.getProgress());
        data.put("failReason", task.getFailReason());
        data.put("bizId", task.getBizId());
        data.put("resultId", expired ? null : task.getResultRef());
        data.put("resultData", resultData);
        data.put("resultExpired", expired);
        data.put("readFlag", task.getReadFlag());
        data.put("createdAt", task.getCreatedAt());
        data.put("finishedAt", task.getFinishedAt());
        return data;
    }

    private List<ParsedEntry> parseEntries(String text) {
        List<ParsedEntry> result = new ArrayList<>();
        String[] blocks = text.split("\\n{2,}");
        for (String block : blocks) {
            String clean = block.replaceAll("\\s+", " ").trim();
            if (clean.length() < 8) {
                continue;
            }
            String type = classify(clean);
            String title = buildTitle(clean, type);
            result.add(new ParsedEntry(type, title, clean));
            if (result.size() >= 12) {
                break;
            }
        }
        if (result.isEmpty()) {
            result.add(new ParsedEntry("other", "简历导入内容", limit(text, 2000)));
        }
        return result;
    }

    private String classify(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (text.contains("大学") || text.contains("学院") || lower.contains("gpa")) {
            return "education";
        }
        if (text.contains("实习") || text.contains("公司")) {
            return "internship";
        }
        if (text.contains("项目") || lower.contains("项目负责人")) {
            return "project";
        }
        if (text.contains("技能") || text.contains("熟悉") || text.contains("掌握")) {
            return "skill";
        }
        if (text.contains("证书") || text.contains("六级") || text.contains("认证")) {
            return "certificate";
        }
        return "other";
    }

    private String buildTitle(String text, String type) {
        String[] pieces = text.split("[，。；：|]");
        String base = pieces.length == 0 ? text : pieces[0];
        base = base.trim();
        if (base.length() > 80) {
            base = base.substring(0, 80);
        }
        return base.isBlank() ? "未命名" + type : base;
    }

    private int typeOrder(String type) {
        return switch (type) {
            case "education" -> 1;
            case "internship" -> 2;
            case "project" -> 3;
            case "skill" -> 4;
            case "certificate" -> 5;
            default -> 6;
        };
    }

    private String buildResumeMarkdown(String nickname, String target,
                                       List<Map<String, Object>> items) {
        StringBuilder markdown = new StringBuilder("# ").append(nickname).append("\n\n");
        if (!target.isBlank()) {
            markdown.append("## 求职意向\n").append(target).append("\n\n");
        }
        for (Map<String, Object> item : items) {
            markdown.append("## ").append(item.get("title")).append("\n")
                    .append("- ").append(item.get("text")).append("\n\n");
        }
        return markdown.toString();
    }

    private List<String> extractSkills(String jd) {
        List<String> result = new ArrayList<>();
        for (String skill : KNOWN_SKILLS) {
            if (jd.toLowerCase(Locale.ROOT).contains(skill.toLowerCase(Locale.ROOT))) {
                result.add(skill);
            }
        }
        return result;
    }

    private int scoreSkills(List<Map<String, Object>> entries, List<String> jdSkills) {
        if (jdSkills.isEmpty()) {
            return 72;
        }
        String resumeText = jsonUtils.write(entries).toLowerCase(Locale.ROOT);
        long matched = jdSkills.stream()
                .filter(skill -> resumeText.contains(skill.toLowerCase(Locale.ROOT)))
                .count();
        return (int) Math.min(98, 45 + Math.round(53.0 * matched / jdSkills.size()));
    }

    private int scoreProjects(List<Map<String, Object>> entries) {
        long count = entries.stream().filter(row ->
                "project".equals(String.valueOf(row.get("entryType")))
                        || "internship".equals(String.valueOf(row.get("entryType")))).count();
        long quantified = entries.stream().filter(row ->
                NUMBER_PATTERN.matcher(String.valueOf(row.get("text"))).find()).count();
        return (int) Math.min(96, 45 + count * 10 + quantified * 5);
    }

    private int scoreEducation(List<Map<String, Object>> entries) {
        boolean hasEducation = entries.stream().anyMatch(row ->
                "education".equals(String.valueOf(row.get("entryType")))
                        || String.valueOf(row.get("title")).contains("大学")
                        || String.valueOf(row.get("title")).contains("学院"));
        return hasEducation ? 80 : 58;
    }

    private int scoreExpression(List<Map<String, Object>> entries) {
        int totalLength = entries.stream()
                .mapToInt(row -> String.valueOf(row.get("text")).length()).sum();
        int quantified = (int) entries.stream()
                .filter(row -> NUMBER_PATTERN.matcher(String.valueOf(row.get("text"))).find())
                .count();
        return Math.min(94, 50 + Math.min(20, totalLength / 40) + quantified * 4);
    }

    private Map<String, Object> dim(String key, String name, double weight, int score) {
        Map<String, Object> dim = new LinkedHashMap<>();
        dim.put("key", key);
        dim.put("name", name);
        dim.put("weight", weight);
        dim.put("score", score);
        return dim;
    }

    private String rewrite(String original) {
        String clean = original.replaceAll("^(参与|负责|完成)", "").trim();
        if (NUMBER_PATTERN.matcher(clean).find()) {
            return "主导" + clean;
        }
        return "负责" + clean + "，并补充可量化的业务结果。";
    }

    private int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 3);
    }

    private List<Map<String, Object>> castList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    map.forEach((key, content) -> row.put(String.valueOf(key), content));
                    result.add(row);
                }
            }
            return result;
        }
        return List.of();
    }

    private Long toLong(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("缺少ID");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private Long nullableLong(Object value) {
        return value == null ? null : toLong(value);
    }

    private String safeMessage(Exception exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "任务执行失败";
        }
        return exception.getMessage();
    }

    private String limit(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private record ParsedEntry(String entryType, String title, String text) {
    }

    private record TaskResult(Long resultRef, String resultJson) {
    }

    /**
     * 小型工厂只用于把运行期错误信息包装成统一业务异常，避免在深层私有方法重复 new。
     */
    private static final class BizExceptionFactory {
        private final String message;

        private BizExceptionFactory(String message) {
            this.message = message;
        }

        private BizException build() {
            return BizException.business(message);
        }
    }
}
