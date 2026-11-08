package com.qznu.jobhelper.support.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qznu.jobhelper.support.config.entity.SystemConfig;
import com.qznu.jobhelper.support.config.mapper.SystemConfigMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置读取与热更新。
 *
 * <p>quota、AI 调用上限、文件保留天数等运行时参数优先读取数据库；
 * 数据库缺键时才使用这里的内置默认值。</p>
 */
@Service
public class ConfigService {

    public static final List<String> CONFIG_KEYS = List.of(
            "quota.daily.default",
            "ai.model",
            "ai.timeout-ms",
            "ai.max-input-tokens",
            "ai.retry.max",
            "ai.task.timeout-min",
            "ai.circuit.fail-rate",
            "ai.circuit.window-min",
            "ai.circuit.max-calls",
            "ai.price.input",
            "ai.price.output",
            "file.keep-days",
            "log.ai.keep-days"
    );

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("quota.daily.default", "50"),
            Map.entry("ai.model", "deepseek-chat"),
            Map.entry("ai.timeout-ms", "60000"),
            Map.entry("ai.max-input-tokens", "6000"),
            Map.entry("ai.retry.max", "3"),
            Map.entry("ai.task.timeout-min", "5"),
            Map.entry("ai.circuit.fail-rate", "0.5"),
            Map.entry("ai.circuit.window-min", "60"),
            Map.entry("ai.circuit.max-calls", "200"),
            Map.entry("ai.price.input", "0.000001"),
            Map.entry("ai.price.output", "0.000002"),
            Map.entry("file.keep-days", "90"),
            Map.entry("log.ai.keep-days", "180")
    );

    private final SystemConfigMapper systemConfigMapper;
    private volatile Map<String, String> cache = Map.of();

    public ConfigService(SystemConfigMapper systemConfigMapper) {
        this.systemConfigMapper = systemConfigMapper;
    }

    @PostConstruct
    public void refresh() {
        Map<String, String> loaded = new LinkedHashMap<>();
        for (SystemConfig item : systemConfigMapper.selectList(null)) {
            loaded.put(item.getConfigKey(), item.getConfigValue());
        }
        cache = Map.copyOf(loaded);
    }

    public String getString(String key) {
        return cache.getOrDefault(key, DEFAULTS.getOrDefault(key, ""));
    }

    public int getInt(String key) {
        return Integer.parseInt(getString(key));
    }

    public long getLong(String key) {
        return Long.parseLong(getString(key));
    }

    public BigDecimal getDecimal(String key) {
        return new BigDecimal(getString(key));
    }

    /**
     * 管理端展示固定 13 个键，数据库缺项时也返回默认值，保证页面稳定。
     */
    public List<Map<String, Object>> listForAdmin() {
        Map<String, SystemConfig> byKey = new LinkedHashMap<>();
        for (SystemConfig item : systemConfigMapper.selectList(
                new LambdaQueryWrapper<SystemConfig>().orderByAsc(SystemConfig::getId))) {
            byKey.put(item.getConfigKey(), item);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String key : CONFIG_KEYS) {
            SystemConfig row = byKey.get(key);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("configKey", key);
            item.put("configValue", row == null ? DEFAULTS.get(key) : row.getConfigValue());
            item.put("valueType", valueType(key));
            item.put("remark", row == null ? defaultRemark(key) : row.getRemark());
            item.put("updatedAt", row == null ? null : row.getUpdatedAt());
            result.add(item);
        }
        return result;
    }

    public void updateAll(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!CONFIG_KEYS.contains(entry.getKey())) {
                throw new IllegalArgumentException("非法配置键: " + entry.getKey());
            }
            validate(entry.getKey(), entry.getValue());
            SystemConfig config = new SystemConfig();
            config.setConfigKey(entry.getKey());
            config.setConfigValue(entry.getValue());
            config.setRemark(defaultRemark(entry.getKey()));
            systemConfigMapper.upsert(config);
        }
        refresh();
    }

    public void saveOne(String key, String value) {
        updateAll(Map.of(key, value));
    }

    private void validate(String key, String value) {
        switch (key) {
            case "quota.daily.default" -> requireRange(value, 0, 999);
            case "ai.timeout-ms" -> requireRange(value, 1000, 600000);
            case "ai.max-input-tokens" -> requireRange(value, 100, 100000);
            case "ai.retry.max" -> requireRange(value, 0, 5);
            case "ai.task.timeout-min" -> requireRange(value, 1, 60);
            case "ai.circuit.fail-rate" -> {
                double rate = Double.parseDouble(value);
                if (rate < 0 || rate > 1) {
                    throw new IllegalArgumentException("熔断失败率必须在 0～1");
                }
            }
            case "ai.circuit.window-min" -> requireRange(value, 1, 1440);
            case "ai.circuit.max-calls" -> requireRange(value, 1, 10000);
            case "ai.price.input", "ai.price.output" -> new BigDecimal(value);
            case "file.keep-days", "log.ai.keep-days" -> requireRange(value, 1, 3650);
            default -> {
            }
        }
    }

    private void requireRange(String value, int min, int max) {
        int parsed = Integer.parseInt(value);
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException("配置值超出范围 " + min + "～" + max);
        }
    }

    private String valueType(String key) {
        if ("ai.model".equals(key)) {
            return "string";
        }
        if ("ai.circuit.fail-rate".equals(key)
                || "ai.price.input".equals(key)
                || "ai.price.output".equals(key)) {
            return "number";
        }
        return "integer";
    }

    private String defaultRemark(String key) {
        return switch (key) {
            case "quota.daily.default" -> "每用户每日 AI 配额默认值";
            case "ai.model" -> "AI 模型标识";
            case "ai.timeout-ms" -> "单次 AI 调用超时毫秒数";
            case "ai.max-input-tokens" -> "单次输入 token 上限";
            case "ai.retry.max" -> "AI 调用额外重试次数";
            case "ai.task.timeout-min" -> "异步任务超时分钟数";
            case "ai.circuit.fail-rate" -> "熔断失败率阈值";
            case "ai.circuit.window-min" -> "熔断统计窗口分钟数";
            case "ai.circuit.max-calls" -> "单用户日 AI 调用上限";
            case "ai.price.input" -> "输入 token 单价（元）";
            case "ai.price.output" -> "输出 token 单价（元）";
            case "file.keep-days" -> "上传文件保留天数";
            case "log.ai.keep-days" -> "AI 调用日志保留天数";
            default -> "";
        };
    }
}
