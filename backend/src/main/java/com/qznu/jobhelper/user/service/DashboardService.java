package com.qznu.jobhelper.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qznu.jobhelper.ability.entity.AbilityEntry;
import com.qznu.jobhelper.ability.mapper.AbilityEntryMapper;
import com.qznu.jobhelper.common.exception.ForbiddenException;
import com.qznu.jobhelper.delivery.entity.DeliveryRecord;
import com.qznu.jobhelper.delivery.mapper.DeliveryRecordMapper;
import com.qznu.jobhelper.diagnosis.entity.ResumeDiagnoseRecord;
import com.qznu.jobhelper.diagnosis.mapper.ResumeDiagnoseRecordMapper;
import com.qznu.jobhelper.resume.entity.UserResume;
import com.qznu.jobhelper.resume.mapper.UserResumeMapper;
import com.qznu.jobhelper.support.quota.QuotaService;
import com.qznu.jobhelper.task.entity.AiTask;
import com.qznu.jobhelper.task.mapper.AiTaskMapper;
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
 * 工作台摘要与任务通知。
 */
@Service
public class DashboardService {

    private final UserMapper userMapper;
    private final AbilityEntryMapper abilityMapper;
    private final UserResumeMapper resumeMapper;
    private final DeliveryRecordMapper deliveryMapper;
    private final ResumeDiagnoseRecordMapper diagnoseMapper;
    private final AiTaskMapper taskMapper;
    private final QuotaService quotaService;
    private final Clock clock;

    public DashboardService(UserMapper userMapper,
                            AbilityEntryMapper abilityMapper,
                            UserResumeMapper resumeMapper,
                            DeliveryRecordMapper deliveryMapper,
                            ResumeDiagnoseRecordMapper diagnoseMapper,
                            AiTaskMapper taskMapper,
                            QuotaService quotaService,
                            Clock clock) {
        this.userMapper = userMapper;
        this.abilityMapper = abilityMapper;
        this.resumeMapper = resumeMapper;
        this.deliveryMapper = deliveryMapper;
        this.diagnoseMapper = diagnoseMapper;
        this.taskMapper = taskMapper;
        this.quotaService = quotaService;
        this.clock = clock;
    }

    public Map<String, Object> summary(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw ForbiddenException.accountInvalid();
        }

        long abilityCount = abilityMapper.selectCount(new LambdaQueryWrapper<AbilityEntry>()
                .eq(AbilityEntry::getUserId, userId)
                .eq(AbilityEntry::getStatus, "confirmed")
                .eq(AbilityEntry::getEnabled, 1));
        long resumeCount = resumeMapper.selectCount(new LambdaQueryWrapper<UserResume>()
                .eq(UserResume::getUserId, userId));
        long deliveryCount = deliveryMapper.selectCount(new LambdaQueryWrapper<DeliveryRecord>()
                .eq(DeliveryRecord::getUserId, userId));
        LocalDateTime monthStart = LocalDateTime.now(clock).withDayOfMonth(1)
                .toLocalDate().atStartOfDay();
        long monthlyCount = diagnoseMapper.selectCount(new LambdaQueryWrapper<ResumeDiagnoseRecord>()
                .eq(ResumeDiagnoseRecord::getUserId, userId)
                .ge(ResumeDiagnoseRecord::getCreatedAt, monthStart));
        ResumeDiagnoseRecord latest = diagnoseMapper.selectOne(
                new LambdaQueryWrapper<ResumeDiagnoseRecord>()
                        .eq(ResumeDiagnoseRecord::getUserId, userId)
                        .orderByDesc(ResumeDiagnoseRecord::getCreatedAt)
                        .last("LIMIT 1"));

        QuotaService.QuotaSnapshot quota = quotaService.remaining(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nickname", user.getNickname());
        data.put("quota", Map.of(
                "quotaUsed", quota.quotaUsed(),
                "quotaLimit", quota.quotaLimit(),
                "remaining", quota.remaining()
        ));
        data.put("abilityCount", abilityCount);
        data.put("resumeCount", resumeCount);
        data.put("deliveryCount", deliveryCount);
        data.put("monthlyDiagnosisCount", monthlyCount);
        data.put("latestDiagnosis", latest == null ? null : Map.of(
                "id", latest.getId(),
                "position", latest.getPositionSnapshot() == null ? "" : latest.getPositionSnapshot(),
                "score", latest.getScore() == null ? 0 : latest.getScore(),
                "status", latest.getStatus(),
                "degraded", "degraded".equals(latest.getStatus()),
                "createdAt", latest.getCreatedAt()
        ));
        return data;
    }

    public List<Map<String, Object>> notices(Long userId, int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiTask task : taskMapper.selectRecentNotices(userId, limit)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", task.getId());
            item.put("taskType", task.getTaskType());
            item.put("status", task.getStatus());
            item.put("title", switch (task.getTaskType()) {
                case "resume_parse" -> "简历解析";
                case "resume_generate" -> "简历生成";
                case "diagnose" -> "简历诊断";
                case "interview_gen" -> "模拟面试出题";
                default -> "AI 任务";
            });
            item.put("resultId", task.getResultRef());
            item.put("readFlag", task.getReadFlag());
            item.put("failReason", task.getFailReason());
            item.put("createdAt", task.getCreatedAt());
            result.add(item);
        }
        return result;
    }
}
