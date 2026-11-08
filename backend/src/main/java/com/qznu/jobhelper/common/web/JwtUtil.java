package com.qznu.jobhelper.common.web;

import com.qznu.jobhelper.common.exception.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

public class JwtUtil {

    private final SecretKey key;
    private final long expireDays;
    private final Clock clock;

    public JwtUtil(String secret, long expireDays, Clock clock) {
        if (secret == null) {
            throw new IllegalArgumentException("JWT secret must not be null");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        if (expireDays <= 0) {
            throw new IllegalArgumentException("JWT expireDays must be positive");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expireDays = expireDays;
        this.clock = clock;
        validateSecret();
    }

    public void validateSecret() {
        byte[] encoded = key.getEncoded();
        if (encoded == null || encoded.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
    }

    public String generate(Long userId, String role) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(expireDays, ChronoUnit.DAYS);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public JwtPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Date issuedAt = claims.getIssuedAt();
            Date expiresAt = claims.getExpiration();
            if (issuedAt == null || expiresAt == null) {
                throw new AuthException();
            }
            return new JwtPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get("role", String.class),
                    issuedAt.toInstant(),
                    expiresAt.toInstant()
            );
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AuthException();
        }
    }
}
