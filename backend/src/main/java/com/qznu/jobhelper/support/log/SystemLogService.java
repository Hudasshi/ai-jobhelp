package com.qznu.jobhelper.support.log;

import com.qznu.jobhelper.support.log.entity.SystemLog;
import com.qznu.jobhelper.support.log.mapper.SystemLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 审计日志写入服务。
 *
 * <p>日志失败不能改变主业务结果，因此异常只记录到应用日志。</p>
 */
@Slf4j
@Service
public class SystemLogService {

    /**
     * system_log.action 的冻结白名单。
     * 在写入层再次校验，保证未来新增业务代码误用动作名时不会污染审计数据。
     */
    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "login", "login_fail", "quota_adjust", "delete_all",
            "user_disable", "user_enable", "file_clean", "log_clean",
            "circuit_break");

    private final SystemLogMapper systemLogMapper;
    private final Clock clock;

    public SystemLogService(SystemLogMapper systemLogMapper, Clock clock) {
        this.systemLogMapper = systemLogMapper;
        this.clock = clock;
    }

    public void write(Long userId, String username, String action,
                      boolean success, String ip, String remark) {
        try {
            if (!ALLOWED_ACTIONS.contains(action)) {
                log.error("拒绝写入非法系统日志动作 action={}", action);
                return;
            }
            SystemLog item = new SystemLog();
            item.setUserId(userId);
            item.setUsername(username);
            item.setAction(action);
            item.setSuccess(success ? 1 : 0);
            item.setIp(ip);
            item.setRemark(limit(remark, 500));
            item.setCreatedAt(LocalDateTime.now(clock));
            systemLogMapper.insert(item);
        } catch (Exception exception) {
            log.error("写系统日志失败 action={}", action, exception);
        }
    }

    private String limit(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
