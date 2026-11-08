package com.qznu.jobhelper.common.config;

import com.qznu.jobhelper.common.web.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 与 Web 拦截器无关的基础 Bean。
 *
 * <p>保持依赖方向单向：本配置提供 JwtUtil，JwtInterceptor 使用 JwtUtil，
 * WebConfig 只使用构造完成的拦截器。这样 Spring Boot 可以按正常顺序创建 Bean。</p>
 */
@Configuration
public class CommonBeanConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // cost=10 与初始化 SQL 中的演示账号一致，兼顾安全性与登录耗时。
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public JwtUtil jwtUtil(@Value("${jwt.secret}") String secret,
                           @Value("${jwt.expire-days:7}") long expireDays,
                           Clock clock) {
        return new JwtUtil(secret, expireDays, clock);
    }
}
