package com.qznu.jobhelper.common.web;

import java.time.Instant;

public record JwtPrincipal(Long userId, String role, Instant issuedAt, Instant expiresAt) {
}
