package com.qznu.jobhelper.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigTest {

    @Test
    void applicationYmlBindsRequiredConfiguration() throws IOException {
        MockEnvironment environment = new MockEnvironment();

        List<PropertySource<?>> yamlSources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        yamlSources.forEach(environment.getPropertySources()::addLast);

        environment.getPropertySources().addFirst(new MapPropertySource(
                "testSecrets",
                Map.of("JWT_SECRET", "0123456789abcdef0123456789abcdef")
        ));

        Binder binder = Binder.get(environment);

        assertThat(required(binder, "ai.provider", String.class))
                .isEqualTo("deepseek");
        assertThat(required(binder, "ai.base-url", String.class))
                .isEqualTo("https://api.deepseek.com");
        assertThat(required(binder, "ai.model", String.class))
                .isEqualTo("deepseek-chat");
        assertThat(required(binder, "ai.timeout-ms", Integer.class))
                .isEqualTo(60000);
        assertThat(required(binder, "ai.max-input-tokens", Integer.class))
                .isEqualTo(6000);
        assertThat(required(binder, "ai.retry.max", Integer.class))
                .isEqualTo(3);

        assertThat(required(binder, "spring.datasource.url", String.class))
                .contains("ai_jobs");
        assertThat(required(binder, "spring.data.redis.port", Integer.class))
                .isEqualTo(6379);
        assertThat(required(binder, "spring.servlet.multipart.max-file-size", DataSize.class)
                .toMegabytes())
                .isEqualTo(10L);
        assertThat(required(binder, "spring.servlet.multipart.max-request-size", DataSize.class)
                .toMegabytes())
                .isEqualTo(12L);

        String jwtSecret = required(binder, "jwt.secret", String.class);
        assertThat(jwtSecret).hasSizeGreaterThanOrEqualTo(32);

        assertThat(required(binder, "jwt.expire-days", Integer.class))
                .isEqualTo(7);
        assertThat(required(binder, "file.upload-dir", String.class))
                .isNotBlank();
    }

    private static <T> T required(Binder binder, String key, Class<T> type) {
        return binder.bind(key, type)
                .orElseThrow(() -> new IllegalStateException("缺少配置项: " + key));
    }
}
