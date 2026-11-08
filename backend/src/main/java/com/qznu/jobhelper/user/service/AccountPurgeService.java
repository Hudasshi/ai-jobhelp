package com.qznu.jobhelper.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qznu.jobhelper.ability.entity.AbilityEntry;
import com.qznu.jobhelper.ability.mapper.AbilityEntryMapper;
import com.qznu.jobhelper.common.exception.BizException;
import com.qznu.jobhelper.common.exception.ForbiddenException;
import com.qznu.jobhelper.delivery.entity.DeliveryRecord;
import com.qznu.jobhelper.delivery.mapper.DeliveryRecordMapper;
import com.qznu.jobhelper.diagnosis.entity.ResumeDiagnoseRecord;
import com.qznu.jobhelper.diagnosis.mapper.ResumeDiagnoseRecordMapper;
import com.qznu.jobhelper.interview.entity.MockInterviewRecord;
import com.qznu.jobhelper.interview.mapper.MockInterviewRecordMapper;
import com.qznu.jobhelper.resume.entity.UserResume;
import com.qznu.jobhelper.resume.mapper.UserResumeMapper;
import com.qznu.jobhelper.support.log.SystemLogService;
import com.qznu.jobhelper.support.quota.QuotaService;
import com.qznu.jobhelper.task.entity.AiCallLog;
import com.qznu.jobhelper.task.entity.AiTask;
import com.qznu.jobhelper.task.mapper.AiCallLogMapper;
import com.qznu.jobhelper.task.mapper.AiTaskMapper;
import com.qznu.jobhelper.user.entity.User;
import com.qznu.jobhelper.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 账号注销与个人数据清理。
 *
 * <p>用户行保留为逻辑删除，system_log 保留审计快照；
 * 诊断正文、AI 日志中的用户标识和业务数据按文档边界清理。</p>
 */
@Service
public class AccountPurgeService {

    private final UserMapper userMapper;
    private final AbilityEntryMapper abilityEntryMapper;
    private final UserResumeMapper userResumeMapper;
    private final DeliveryRecordMapper deliveryRecordMapper;
    private final ResumeDiagnoseRecordMapper diagnoseMapper;
    private final MockInterviewRecordMapper interviewMapper;
    private final AiTaskMapper aiTaskMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final SystemLogService systemLogService;
    private final QuotaService quotaService;
    private final Clock clock;

    public AccountPurgeService(UserMapper userMapper,
                               AbilityEntryMapper abilityEntryMapper,
                               UserResumeMapper userResumeMapper,
                               DeliveryRecordMapper deliveryRecordMapper,
                               ResumeDiagnoseRecordMapper diagnoseMapper,
                               MockInterviewRecordMapper interviewMapper,
                               AiTaskMapper aiTaskMapper,
                               AiCallLogMapper aiCallLogMapper,
                               SystemLogService systemLogService,
                               QuotaService quotaService,
                               Clock clock) {
        this.userMapper = userMapper;
        this.abilityEntryMapper = abilityEntryMapper;
        this.userResumeMapper = userResumeMapper;
        this.deliveryRecordMapper = deliveryRecordMapper;
        this.diagnoseMapper = diagnoseMapper;
        this.interviewMapper = interviewMapper;
        this.aiTaskMapper = aiTaskMapper;
        this.aiCallLogMapper = aiCallLogMapper;
        this.systemLogService = systemLogService;
        this.quotaService = quotaService;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> purge(Long userId, String confirmedUsername, String ip) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw ForbiddenException.accountInvalid();
        }
        if (!user.getUsername().equals(confirmedUsername)) {
            throw BizException.badRequest("用户名不匹配");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        userMapper.markDeleted(userId, now);

        abilityEntryMapper.delete(new LambdaQueryWrapper<AbilityEntry>()
                .eq(AbilityEntry::getUserId, userId));
        userResumeMapper.delete(new LambdaQueryWrapper<UserResume>()
                .eq(UserResume::getUserId, userId));
        deliveryRecordMapper.delete(new LambdaQueryWrapper<DeliveryRecord>()
                .eq(DeliveryRecord::getUserId, userId));
        interviewMapper.delete(new LambdaQueryWrapper<MockInterviewRecord>()
                .eq(MockInterviewRecord::getUserId, userId));
        aiTaskMapper.delete(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getUserId, userId));

        diagnoseMapper.update(null, new LambdaUpdateWrapper<ResumeDiagnoseRecord>()
                .eq(ResumeDiagnoseRecord::getUserId, userId)
                .set(ResumeDiagnoseRecord::getUserId, null)
                .set(ResumeDiagnoseRecord::getUsernameSnapshot, "已注销")
                .set(ResumeDiagnoseRecord::getResumeId, null)
                .set(ResumeDiagnoseRecord::getJobTargetId, null)
                .set(ResumeDiagnoseRecord::getDeliveryId, null)
                .set(ResumeDiagnoseRecord::getTaskId, null)
                .set(ResumeDiagnoseRecord::getReportJson, null));
        aiCallLogMapper.update(null, new LambdaUpdateWrapper<AiCallLog>()
                .eq(AiCallLog::getUserId, userId)
                .set(AiCallLog::getUserId, null));

        quotaService.resetToday(userId);
        systemLogService.write(userId, user.getUsername(), "delete_all",
                true, ip, "用户主动注销并清理个人数据");
        return Map.of("deleted", true);
    }
}
