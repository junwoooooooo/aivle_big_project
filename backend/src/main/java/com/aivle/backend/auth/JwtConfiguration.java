package com.aivle.backend.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import com.aivle.backend.user.repository.UserRepository;
import com.aivle.backend.common.entity.UserStatus;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {
    @Bean
    SecretKey jwtSecretKey(JwtProperties properties) {
        return new SecretKeySpec(
            properties.secret().getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
        );
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new LegacyAwarePasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean("accessTokenDecoder")
    JwtDecoder accessTokenDecoder(
        SecretKey jwtSecretKey,
        JwtProperties properties,
        UserRepository users
    ) {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) decoder(jwtSecretKey, properties, JwtTokenService.ACCESS_TYPE);
        OAuth2TokenValidator<Jwt> securityVersionValidator = jwt -> {
            try {
                Long userId = Long.valueOf(jwt.getSubject());
                Number claimedVersion = jwt.getClaim("securityVersion");
                return users.findByIdAndDeletedAtIsNull(userId)
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE
                        && claimedVersion != null
                        && user.getSecurityVersion().equals(claimedVersion.longValue()))
                    .isPresent()
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Session has been revoked", null));
            } catch (RuntimeException exception) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid security snapshot", null));
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(properties.clockSkew()),
            new JwtIssuerValidator(properties.issuer()),
            jwt -> JwtTokenService.ACCESS_TYPE.equals(jwt.getClaimAsString("tokenType"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Unexpected token type", null)),
            securityVersionValidator
        ));
        return decoder;
    }

    @Bean("refreshTokenDecoder")
    JwtDecoder refreshTokenDecoder(
        SecretKey jwtSecretKey,
        JwtProperties properties
    ) {
        return decoder(jwtSecretKey, properties, JwtTokenService.REFRESH_TYPE);
    }

    private JwtDecoder decoder(
        SecretKey key,
        JwtProperties properties,
        String requiredTokenType
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        OAuth2TokenValidator<Jwt> tokenTypeValidator = jwt -> {
            if (requiredTokenType.equals(jwt.getClaimAsString("tokenType"))) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "Unexpected token type",
                null
            ));
        };
        JwtTimestampValidator timestampValidator =
            new JwtTimestampValidator(properties.clockSkew());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            timestampValidator,
            new JwtIssuerValidator(properties.issuer()),
            tokenTypeValidator
        ));
        return decoder;
    }
}
