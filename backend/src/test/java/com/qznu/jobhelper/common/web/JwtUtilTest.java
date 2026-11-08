package com.qznu.jobhelper.common.web;

import com.qznu.jobhelper.common.exception.AuthException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");

    private final JwtUtil jwtUtil = new JwtUtil(
            "0123456789abcdef0123456789abcdef",
            7,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void generateAndParse() {
        String token = jwtUtil.generate(1L, "user");

        JwtPrincipal principal = jwtUtil.parse(token);

        assertThat(principal.userId()).isEqualTo(1L);
        assertThat(principal.role()).isEqualTo("user");
        assertThat(principal.issuedAt()).isEqualTo(NOW);
        assertThat(principal.expiresAt()).isEqualTo(NOW.plus(7, ChronoUnit.DAYS));
    }

    @Test
    void tamperedTokenThrowsAuthException() {
        String token = jwtUtil.generate(1L, "user");
        char[] chars = token.toCharArray();
        int signatureStart = token.lastIndexOf('.') + 1;
        chars[signatureStart] = chars[signatureStart] == 'a' ? 'b' : 'a';

        assertThatThrownBy(() -> jwtUtil.parse(new String(chars)))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void shortSecretIsRejected() {
        assertThatThrownBy(() -> new JwtUtil(
                "too-short",
                7,
                Clock.fixed(NOW, ZoneOffset.UTC)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
