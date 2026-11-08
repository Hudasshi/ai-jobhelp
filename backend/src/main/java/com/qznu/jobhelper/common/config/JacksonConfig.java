package com.qznu.jobhelper.common.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * 统一前后端日期时间格式。
 *
 * <p>Spring 的 {@code spring.jackson.date-format} 只影响旧版 Date，不会自动改变
 * Java 8 时间类型。这里显式注册 LocalDate/LocalDateTime 的序列化器，确保接口
 * 和 OpenAPI 契约一致，前端无需兼容 ISO 中的 T 字符。</p>
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer javaTimeCustomizer() {
        return builder -> {
            builder.serializers(
                    new LocalDateTimeSerializer(DATE_TIME),
                    new LocalDateSerializer(DATE)
            );
            builder.deserializers(
                    new LocalDateTimeDeserializer(DATE_TIME),
                    new LocalDateDeserializer(DATE)
            );
        };
    }
}
