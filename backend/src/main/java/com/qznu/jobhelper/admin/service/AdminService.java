package com.qznu.jobhelper.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qznu.jobhelper.admin.dto.ConfigItem;
import com.qznu.jobhelper.admin.dto.ConfigUpdateRequest;
import com.qznu.jobhelper.admin.dto.QuotaOverrideRequest;
import com.qznu.jobhelper.admin.dto.UserStatusRequest;
import com.qznu.jobhelper.common.exception.BizException;
import com.qznu.jobhelper.common.exception.ForbiddenException;
import com.qznu.jobhelper.diagnosis.entity.ResumeDiagnoseRecord;
import com.qznu.jobhelper.diagnosis.mapper.ResumeDiagnoseRecordMapper;
import com.qznu.jobhelper.support.config.ConfigService;
import com.qznu.jobhelper.support.log.SystemLogService;
import com.qznu.jobhelper.support.log.entity.SystemLog;
import com.qznu.jobhelper.support.log.mapper.SystemLogMapper;
import com.qznu.jobhelper.support.quota.QuotaService;
import com.qznu.jobhelper.task.entity.AiCallLog;
import com.qznu.jobhelper.task.mapper.AiCallLogMapper;
import com.qznu.jobhelper.user.entity.User;
import com.qznu.jobhelper.user.mapper.UserMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端聚合服务。
 *
 * <p>管理端可以查看跨用户统计与元数据，但用户密码、Prompt、JD、简历正文、报告
 * 正文都不在返回白名单内。任何用户写操作在成功后追加系统审计日志，审计失败不会
 * 回滚已完成的业务操作，这是治理日志与业务事务的明确边界。</p>
 */
@Service
public class AdminService {

    private static final Set<String> USER_FILTERS = Set.of("normal", "disabled", "deleted");
    private static final Set<String> DIAGNOSIS_STATUSES = Set.of("success", "degraded", "failed");
    private static final Set<String> AI_SCENES = Set.of(
            "resume_parse", "resume_generate", "diagnose", "jd_parse",
            "interview_gen", "interview_feedback");
    /**
     * 冻结契约只允许九类审计动作。
     * 不能把注册、登出、能力编辑等普通业务操作扩展成审计动作；
     * 这些操作要么没有审计要求，要么由应用日志记录。
     */
    private static final Set<String> LOG_ACTIONS = Set.of(
            "login", "login_fail", "quota_adjust", "delete_all",
            "user_disable", "user_enable", "file_clean", "log_clean",
            "circuit_break");

    private final JdbcTemplate jdbcTemplate;
    private final UserMapper userMapper;
    private final ResumeDiagnoseRecordMapper diagnoseMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final SystemLogMapper systemLogMapper;
    private final ConfigService configService;
    private final QuotaService quotaService;
    private final SystemLogService systemLogService;
    private final Clock clock;

    public AdminService(JdbcTemplate jdbcTemplate,
                        UserMapper userMapper,
                        ResumeDiagnoseRecordMapper diagnoseMapper,
                        AiCallLogMapper aiCallLogMapper,
                        SystemLogMapper systemLogMapper,
                        ConfigService configService,
                        QuotaService quotaService,
                        SystemLogService systemLogService,
                        Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.userMapper = userMapper;
        this.diagnoseMapper = diagnoseMapper;
        this.aiCallLogMapper = aiCallLogMapper;
        this.systemLogMapper = systemLogMapper;
        this.configService = configService;
        this.quotaService = quotaService;
        this.systemLogService = systemLogService;
        this.clock = clock;
    }

    /**
     * 管理端统计。七个自然日成本是固定口径，不受前端 days=30 切换影响。
     */
    public Map<String, Object> stats(int days) {
        if (days != 7 && days != 30) {
            throw BizException.badRequest("days只能为7或30");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime trendStart = today.minusDays(days - 1L).atStartOfDay();
        LocalDateTime sevenDayStart = today.minusDays(6).atStartOfDay();

        long userTotal = userMapper.countAllIncludingDeleted();
        long active = count("SELECT COUNT(*) FROM `user` WHERE status=1 AND deleted=0");
        long todayDiagnosis = count(
                "SELECT COUNT(*) FROM resume_diagnose_record WHERE created_at>=? AND created_at<?",
                todayStart, tomorrowStart);
        long todayAiCalls = count(
                "SELECT COUNT(*) FROM ai_call_log WHERE created_at>=? AND created_at<?",
                todayStart, tomorrowStart);
        long successCalls = count(
                "SELECT COUNT(*) FROM ai_call_log WHERE status='success' AND created_at>=?",
                trendStart);
        long allCalls = count(
                "SELECT COUNT(*) FROM ai_call_log WHERE created_at>=?", trendStart);
        BigDecimal successRate = allCalls == 0
                ? null
                : BigDecimal.valueOf(successCalls)
                        .divide(BigDecimal.valueOf(allCalls), 4, RoundingMode.HALF_UP);
        BigDecimal sevenDayCost = decimal(
                "SELECT COALESCE(SUM(cost_estimate),0) FROM ai_call_log WHERE created_at>=?",
                sevenDayStart);

        Map<String, Long> distribution = new LinkedHashMap<>();
        for (String status : List.of("pending", "applied", "exam", "interview", "offer", "rejected")) {
            distribution.put(status, 0L);
        }
        jdbcTemplate.query("""
                SELECT status, COUNT(*) AS amount
                FROM delivery_record
                WHERE deleted=0
                GROUP BY status
                """, rs -> {
            distribution.put(rs.getString("status"), rs.getLong("amount"));
        });

        Map<String, Long> byDate = new HashMap<>();
        jdbcTemplate.query("""
                SELECT DATE(created_at) AS call_date, COUNT(*) AS amount
                FROM ai_call_log
                WHERE created_at>=?
                GROUP BY DATE(created_at)
                """, rs -> {
            byDate.put(rs.getDate("call_date").toLocalDate().toString(), rs.getLong("amount"));
        }, trendStart);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date);
            point.put("count", byDate.getOrDefault(date.toString(), 0L));
            trend.add(point);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userTotal", userTotal);
        data.put("activeUserCount", active);
        data.put("todayDiagnosisCount", todayDiagnosis);
        data.put("todayAiCallCount", todayAiCalls);
        data.put("aiSuccessRate", successRate);
        data.put("sevenDayCost", sevenDayCost);
        data.put("deliveryStatusDistribution", distribution);
        data.put("aiCallTrend", trend);
        return data;
    }

    public Map<String, Object> pageUsers(String keyword, String displayStatus,
                                         LocalDate startDate, LocalDate endDate,
                                         long page, long size) {
        String status = blankToNull(displayStatus);
        if (status != null && !USER_FILTERS.contains(status)) {
            throw BizException.badRequest("用户状态筛选不合法");
        }
        validateDateRange(startDate, endDate);
        String where = userWhere(keyword, status, startDate, endDate);
        List<Object> args = userArgs(keyword, status, startDate, endDate);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `user`" + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> records = jdbcTemplate.query("""
                        SELECT id, username, nickname, email, role, deleted, status, created_at
                        FROM `user`%s
                        ORDER BY created_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """.formatted(where),
                pageArgs.toArray(),
                (rs, rowNum) -> {
                    long id = rs.getLong("id");
                    QuotaService.QuotaSnapshot quota = quotaService.remaining(id);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", id);
                    row.put("username", rs.getString("username"));
                    row.put("nickname", rs.getString("nickname"));
                    row.put("email", rs.getString("email"));
                    row.put("displayStatus", displayStatus(rs.getInt("status"), rs.getInt("deleted")));
                    row.put("role", rs.getString("role"));
                    row.put("registeredAt", localDateTime(rs.getTimestamp("created_at")));
                    row.put("quotaUsed", quota.quotaUsed());
                    row.put("quotaLimit", quota.quotaLimit());
                    return row;
                });
        return page(records, total == null ? 0 : total, page, size);
    }

    public Map<String, Object> userDetail(Long id) {
        User user = userMapper.selectAnyById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        QuotaService.QuotaSnapshot quota = quotaService.remaining(id);
        List<Map<String, Object>> recent = diagnoseMapper.selectList(
                        new LambdaQueryWrapper<ResumeDiagnoseRecord>()
                                .eq(ResumeDiagnoseRecord::getUserId, id)
                                .orderByDesc(ResumeDiagnoseRecord::getCreatedAt)
                                .orderByDesc(ResumeDiagnoseRecord::getId)
                                .last("LIMIT 3"))
                .stream()
                .map(this::diagnosisSummary)
                .toList();
        Map<String, Object> data = userBase(user);
        data.put("quotaUsed", quota.quotaUsed());
        data.put("quotaLimit", quota.quotaLimit());
        data.put("lastLoginAt", user.getLastLoginAt());
        data.put("recentDiagnoses", recent);
        return data;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> adjustQuota(Long adminId, Long targetId,
                                           QuotaOverrideRequest request, String ip) {
        User target = userMapper.selectAnyById(targetId);
        if (target == null || Integer.valueOf(1).equals(target.getDeleted())) {
            throw BizException.notFound("用户不存在");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int changed = userMapper.updateQuotaOverride(targetId, request.override(), now);
        if (changed == 0) {
            throw BizException.notFound("用户不存在");
        }
        User updated = userMapper.selectAnyById(targetId);
        quotaService.resetToday(targetId);
        audit(adminId, "quota_adjust",
                "targetUserId=" + targetId + ",override=" + request.override()
                        + ",generation=" + updated.getQuotaGeneration(), ip);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("quotaUsed", 0);
        data.put("quotaLimit", request.override());
        data.put("remaining", request.override());
        data.put("quotaGeneration", updated.getQuotaGeneration());
        return data;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> changeUserStatus(Long adminId, Long targetId,
                                                UserStatusRequest request, String ip) {
        if (Integer.valueOf(0).equals(request.status()) && adminId.equals(targetId)) {
            throw BizException.badRequest("不能禁用当前登录的管理员账号");
        }
        User target = userMapper.selectAnyById(targetId);
        if (target == null) {
            throw BizException.notFound("用户不存在");
        }
        if (Integer.valueOf(1).equals(target.getDeleted())) {
            throw BizException.business("该账号已注销，无法恢复或变更");
        }
        if (!request.status().equals(target.getStatus())) {
            userMapper.updateStatus(targetId, request.status(), LocalDateTime.now(clock));
            audit(adminId,
                    request.status() == 0 ? "user_disable" : "user_enable",
                    "targetUserId=" + targetId + ",status=" + request.status(), ip);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", targetId);
        data.put("displayStatus", request.status() == 1 ? "normal" : "disabled");
        return data;
    }

    public Map<String, Object> pageDiagnoses(Long userId, String status,
                                             LocalDate startDate, LocalDate endDate,
                                             long page, long size) {
        if (userId != null && userId < 1) {
            throw BizException.badRequest("userId必须大于0");
        }
        if (status != null && !status.isBlank() && !DIAGNOSIS_STATUSES.contains(status)) {
            throw BizException.badRequest("诊断状态筛选不合法");
        }
        validateDateRange(startDate, endDate);
        LambdaQueryWrapper<ResumeDiagnoseRecord> query =
                new LambdaQueryWrapper<ResumeDiagnoseRecord>()
                        .eq(userId != null, ResumeDiagnoseRecord::getUserId, userId)
                        .eq(status != null && !status.isBlank(),
                                ResumeDiagnoseRecord::getStatus, status)
                        .ge(startDate != null, ResumeDiagnoseRecord::getCreatedAt,
                                startDate == null ? null : startDate.atStartOfDay())
                        .lt(endDate != null, ResumeDiagnoseRecord::getCreatedAt,
                                endDate == null ? null : endDate.plusDays(1).atStartOfDay())
                        .orderByDesc(ResumeDiagnoseRecord::getCreatedAt)
                        .orderByDesc(ResumeDiagnoseRecord::getId);
        long total = diagnoseMapper.selectCount(query);
        query.last("LIMIT " + size + " OFFSET " + ((page - 1) * size));
        List<Map<String, Object>> records = diagnoseMapper.selectList(query).stream()
                .map(record -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", record.getId());
                    row.put("userId", record.getUserId());
                    row.put("username", record.getUsernameSnapshot());
                    row.put("position", record.getPositionSnapshot());
                    row.put("createdAt", record.getCreatedAt());
                    row.put("costSeconds", record.getCostSeconds());
                    row.put("tokens", record.getTokens());
                    row.put("status", record.getStatus());
                    row.put("degraded", "degraded".equals(record.getStatus()));
                    row.put("failReason", record.getFailReason());
                    return row;
                })
                .toList();
        return page(records, total, page, size);
    }

    public Map<String, Object> pageAiLogs(String scene, String status,
                                          LocalDate startDate, LocalDate endDate,
                                          long page, long size) {
        if (scene != null && !scene.isBlank() && !AI_SCENES.contains(scene)) {
            throw BizException.badRequest("AI场景筛选不合法");
        }
        if (status != null && !status.isBlank() && !Set.of("success", "fail").contains(status)) {
            throw BizException.badRequest("AI状态筛选不合法");
        }
        validateDateRange(startDate, endDate);
        LambdaQueryWrapper<AiCallLog> query = new LambdaQueryWrapper<AiCallLog>()
                .eq(scene != null && !scene.isBlank(), AiCallLog::getScene, scene)
                .eq(status != null && !status.isBlank(), AiCallLog::getStatus, status)
                .ge(startDate != null, AiCallLog::getCreatedAt,
                        startDate == null ? null : startDate.atStartOfDay())
                .lt(endDate != null, AiCallLog::getCreatedAt,
                        endDate == null ? null : endDate.plusDays(1).atStartOfDay())
                .orderByDesc(AiCallLog::getCreatedAt)
                .orderByDesc(AiCallLog::getId);
        long total = aiCallLogMapper.selectCount(query);
        query.last("LIMIT " + size + " OFFSET " + ((page - 1) * size));
        Map<Long, String> usernameCache = new HashMap<>();
        List<Map<String, Object>> records = aiCallLogMapper.selectList(query).stream()
                .map(log -> {
                    String username = null;
                    if (log.getUserId() != null) {
                        username = usernameCache.computeIfAbsent(log.getUserId(), id -> {
                            User user = userMapper.selectAnyById(id);
                            return user == null ? null : user.getUsername();
                        });
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", log.getId());
                    row.put("createdAt", log.getCreatedAt());
                    row.put("userId", log.getUserId());
                    row.put("username", username);
                    row.put("scene", log.getScene());
                    row.put("model", log.getModel());
                    row.put("promptTokens", log.getPromptTokens());
                    row.put("completionTokens", log.getCompletionTokens());
                    row.put("durationMs", log.getDurationMs());
                    row.put("status", log.getStatus());
                    row.put("degraded", Integer.valueOf(1).equals(log.getDegraded()));
                    row.put("costEstimate", log.getCostEstimate());
                    return row;
                })
                .toList();
        return page(records, total, page, size);
    }

    public Map<String, Object> pageSystemLogs(String action, Integer result,
                                              LocalDate startDate, LocalDate endDate,
                                              long page, long size) {
        if (action != null && !action.isBlank() && !LOG_ACTIONS.contains(action)) {
            throw BizException.badRequest("日志动作筛选不合法");
        }
        if (result != null && result != 0 && result != 1) {
            throw BizException.badRequest("日志结果只能为0或1");
        }
        validateDateRange(startDate, endDate);
        LambdaQueryWrapper<SystemLog> query = new LambdaQueryWrapper<SystemLog>()
                .eq(action != null && !action.isBlank(), SystemLog::getAction, action)
                .eq(result != null, SystemLog::getSuccess, result)
                .ge(startDate != null, SystemLog::getCreatedAt,
                        startDate == null ? null : startDate.atStartOfDay())
                .lt(endDate != null, SystemLog::getCreatedAt,
                        endDate == null ? null : endDate.plusDays(1).atStartOfDay())
                .orderByDesc(SystemLog::getCreatedAt)
                .orderByDesc(SystemLog::getId);
        long total = systemLogMapper.selectCount(query);
        query.last("LIMIT " + size + " OFFSET " + ((page - 1) * size));
        List<Map<String, Object>> records = systemLogMapper.selectList(query).stream()
                .map(log -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", log.getId());
                    row.put("createdAt", log.getCreatedAt());
                    row.put("userId", log.getUserId());
                    row.put("username", log.getUsername());
                    row.put("action", log.getAction());
                    row.put("success", Integer.valueOf(1).equals(log.getSuccess()));
                    row.put("ip", log.getIp());
                    row.put("remark", log.getRemark());
                    return row;
                })
                .toList();
        return page(records, total, page, size);
    }

    public Map<String, Object> configs() {
        return Map.of("items", configService.listForAdmin());
    }

    public Map<String, Object> updateConfigs(ConfigUpdateRequest request) {
        Set<String> keys = new HashSet<>();
        Map<String, String> values = new LinkedHashMap<>();
        for (ConfigItem item : request.items()) {
            if (!keys.add(item.configKey())) {
                throw BizException.badRequest("配置键不能重复");
            }
            values.put(item.configKey(), item.configValue());
        }
        try {
            configService.updateAll(values);
        } catch (IllegalArgumentException exception) {
            throw BizException.badRequest(exception.getMessage());
        }
        return configs();
    }

    private void audit(Long adminId, String action, String remark, String ip) {
        User admin = userMapper.selectAnyById(adminId);
        systemLogService.write(adminId, admin == null ? "admin" : admin.getUsername(),
                action, true, ip, remark);
    }

    private Map<String, Object> userBase(User user) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("username", user.getUsername());
        row.put("nickname", user.getNickname());
        row.put("email", user.getEmail());
        row.put("displayStatus", displayStatus(user.getStatus(), user.getDeleted()));
        row.put("role", user.getRole());
        row.put("registeredAt", user.getCreatedAt());
        return row;
    }

    private Map<String, Object> diagnosisSummary(ResumeDiagnoseRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", record.getId());
        row.put("position", record.getPositionSnapshot());
        row.put("score", record.getScore());
        row.put("status", record.getStatus());
        row.put("degraded", "degraded".equals(record.getStatus()));
        row.put("createdAt", record.getCreatedAt());
        return row;
    }

    private Map<String, Object> page(List<Map<String, Object>> records,
                                     long total, long page, long size) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    private String userWhere(String keyword, String status,
                             LocalDate startDate, LocalDate endDate) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (status != null) {
            switch (status) {
                case "normal" -> where.append(" AND deleted=0 AND status=1");
                case "disabled" -> where.append(" AND deleted=0 AND status=0");
                case "deleted" -> where.append(" AND deleted=1");
                default -> {
                }
            }
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (username LIKE ? OR nickname LIKE ?)");
        }
        if (startDate != null) {
            where.append(" AND created_at>=?");
        }
        if (endDate != null) {
            where.append(" AND created_at<?");
        }
        return where.toString();
    }

    private List<Object> userArgs(String keyword, String status,
                                  LocalDate startDate, LocalDate endDate) {
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (startDate != null) {
            args.add(startDate.atStartOfDay());
        }
        if (endDate != null) {
            args.add(endDate.plusDays(1).atStartOfDay());
        }
        return args;
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private BigDecimal decimal(String sql, Object... args) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw BizException.badRequest("开始日期不能晚于结束日期");
        }
    }

    private String displayStatus(int status, int deleted) {
        if (deleted == 1) {
            return "deleted";
        }
        return status == 1 ? "normal" : "disabled";
    }

    private LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
