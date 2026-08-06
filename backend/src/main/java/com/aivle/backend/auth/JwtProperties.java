package com.aivle.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
    String issuer,
    Duration accessTokenTtl,
    Duration refreshTokenTtl,
    String secret,
    Duration clockSkew
) {
    public JwtProperties {
        if (issuer == null
            || issuer.isBlank()
            || accessTokenTtl == null
            || accessTokenTtl.isNegative()
            || accessTokenTtl.isZero()
            || refreshTokenTtl == null
            || refreshTokenTtl.compareTo(accessTokenTtl) <= 0
            || clockSkew == null
            || clockSkew.isNegative()
            || secret == null
            || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                "JWT issuer, TTLs, clock skew, and a 32-byte secret are required"
            );
        }
    }
}
