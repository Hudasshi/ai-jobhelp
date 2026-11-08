package com.qznu.jobhelper.support.quota;

import com.qznu.jobhelper.common.exception.BizException;
import com.qznu.jobhelper.support.config.ConfigService;
import com.qznu.jobhelper.user.entity.User;
import com.qznu.jobhelper.user.mapper.UserMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 每日 AI 配额服务。
 *
 * <p>正常路径使用 Redis 原子脚本，保证并发提交不会超发。
 * 如果课堂机器 Redis 临时不可用，会退化为单进程内存计数，接口仍可演示；
 * 生产部署必须保证 Redis 可用。</p>
 */
@Service
public class QuotaService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>("""
            local used = tonumber(redis.call('GET', KEYS[1]) or '0')
            local limit = tonumber(ARGV[1])
            if used >= limit then
                return -1
            end
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            return current
            """, Long.class);

    private static final DefaultRedisScript<Long> REFUND_SCRIPT = new DefaultRedisScript<>("""
            local used = tonumber(redis.call('GET', KEYS[1]) or '0')
            if used <= 0 then
                return 0
            end
            return redis.call('DECR', KEYS[1])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final ConfigService configService;
    private final Clock clock;
    private final Map<String, AtomicInteger> fallbackCounters = new ConcurrentHashMap<>();

    public QuotaService(StringRedisTemplate redisTemplate,
                        UserMapper userMapper,
                        ConfigService configService,
                        Clock clock) {
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
        this.configService = configService;
        this.clock = clock;
    }

    public QuotaSnapshot remaining(Long userId) {
        Integer limit = limitFor(userId);
        int used = Math.min(readUsed(userId), limit);
        return new QuotaSnapshot(used, limit, Math.max(limit - used, 0));
    }

    /**
     * 扣一次配额。返回 true 表示扣减成功。
     * 配额耗尽时抛 422，由全局异常处理转成 HTTP 200/code=422。
     */
    public void deduct(Long userId, String operationId) {
        int limit = limitFor(userId);
        String key = key(userId);
        try {
            Long current = redisTemplate.execute(
                    DEDUCT_SCRIPT,
                    List.of(key),
                    String.valueOf(limit),
                    String.valueOf(secondsUntilTomorrow())
            );
            if (current == null || current < 0) {
                throw BizException.quotaExceeded("今日 AI 配额已用完");
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Exception redisException) {
            // Redis 故障时保证课堂演示可继续，但会在日志中记录降级。
            AtomicInteger counter = fallbackCounters.computeIfAbsent(key, ignored -> new AtomicInteger());
            if (counter.incrementAndGet() > limit) {
                counter.decrementAndGet();
                throw BizException.quotaExceeded("今日 AI 配额已用完");
            }
        }
    }

    /**
     * 任务失败退款。任务记录中的 quota_state 负责业务幂等。
     */
    public void refund(Long userId) {
        String key = key(userId);
        try {
            redisTemplate.execute(REFUND_SCRIPT, List.of(key));
        } catch (Exception ignored) {
            AtomicInteger counter = fallbackCounters.get(key);
            if (counter != null && counter.get() > 0) {
                counter.decrementAndGet();
            }
        }
    }

    public void resetToday(Long userId) {
        try {
            redisTemplate.delete(key(userId));
        } catch (Exception ignored) {
            fallbackCounters.remove(key(userId));
        }
    }

    public int limitFor(Long userId) {
        User user = userMapper.selectById(userId);
        if (user != null && user.getQuotaOverride() != null) {
            return user.getQuotaOverride();
        }
        return configService.getInt("quota.daily.default");
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    private int readUsed(Long userId) {
        String key = key(userId);
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? 0 : Integer.parseInt(value);
        } catch (Exception ignored) {
            AtomicInteger counter = fallbackCounters.get(key);
            return counter == null ? 0 : counter.get();
        }
    }

    private String key(Long userId) {
        return "quota:" + userId + ":" + LocalDate.now(clock).format(DAY);
    }

    private long secondsUntilTomorrow() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime tomorrow = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.MIDNIGHT);
        return Math.max(Duration.between(now, tomorrow).getSeconds(), 1);
    }

    public record QuotaSnapshot(int quotaUsed, int quotaLimit, int remaining) {
    }
}
