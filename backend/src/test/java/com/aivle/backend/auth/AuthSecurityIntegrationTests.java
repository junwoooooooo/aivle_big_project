package com.aivle.backend.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jwt-test")
class AuthSecurityIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("delete from audit_events");
        jdbcTemplate.update("delete from refresh_tokens");
        jdbcTemplate.update("delete from users");
    }

    @Test
    void signupNormalizesEmailHashesPasswordWithoutIssuingTokens()
        throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .header("X-Request-Id", "signup-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "  OWNERUSER ",
                      "email": "  OWNER@Example.COM ",
                      "password": "a safe long password",
                      "displayName": "  Owner  "
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.user.email")
                .value("owner@example.com"))
            .andExpect(jsonPath("$.data.user.username").value("owneruser"))
            .andExpect(jsonPath("$.data.user.displayName").value("Owner"))
            .andExpect(jsonPath("$.data.signupCompleted").value(true))
            .andExpect(jsonPath("$.data.tokens").doesNotExist());
        String hash = jdbcTemplate.queryForObject(
            "select password_hash from users where email = ?",
            String.class,
            "owner@example.com"
        );
        assertThat(hash)
            .isNotEqualTo("a safe long password")
            .startsWith("{argon2}")
            .satisfies(value ->
                assertThat(passwordEncoder.matches(
                    "a safe long password",
                    value
                )).isTrue());
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from refresh_tokens", Integer.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from audit_events "
                + "where event_type = 'USER_SIGNED_UP' "
                + "and request_id = 'signup-request'",
            Integer.class
        )).isEqualTo(1);
    }

    @Test
    void loginFailureDoesNotRevealWhetherUsernameExists() throws Exception {
        signup();
        String unknown = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"unknown-user","password":"wrong-password"}
                    """))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        String wrong = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"owneruser","password":"wrong-password"}
                    """))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(unknown, "$.error.code"))
            .isEqualTo("INVALID_CREDENTIALS")
            .isEqualTo(JsonPath.read(wrong, "$.error.code"));
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from audit_events "
                + "where event_type = 'LOGIN_FAILED'",
            Integer.class
        )).isEqualTo(2);
    }

    @Test
    void repeatedLoginFailuresAreRateLimitedByUsernameAndIp() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            var result = mockMvc.perform(post("/api/v1/auth/login")
                    .with(request -> { request.setRemoteAddr("203.0.113.27"); return request; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"username":"rate-limit-user","password":"incorrect passphrase"}
                        """))
                .andExpect(status().isUnauthorized());
            if (attempt == 3) {
                result.andExpect(jsonPath("$.error.loginAttempt.warningLevel").value("CAUTION"))
                    .andExpect(jsonPath("$.error.loginAttempt.remainingAttempts").value(2));
            }
            if (attempt == 4) {
                result.andExpect(jsonPath("$.error.loginAttempt.warningLevel").value("FINAL_WARNING"))
                    .andExpect(jsonPath("$.error.loginAttempt.remainingAttempts").value(1));
            }
        }

        mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> { request.setRemoteAddr("203.0.113.27"); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"rate-limit-user","password":"incorrect passphrase"}
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.error.code").value("LOGIN_RATE_LIMITED"))
            .andExpect(jsonPath("$.error.loginAttempt.warningLevel").value("LIMITED"))
            .andExpect(jsonPath("$.error.loginAttempt.remainingAttempts").value(0));
    }

    @Test
    void duplicateEmailPasswordPolicyAndInactiveAccountAreRejected()
        throws Exception {
        signup();
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username":"duplicateuser",
                      "email":"OWNER@example.com",
                      "password":"another safe long password",
                      "displayName":"Duplicate"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code")
                .value("EMAIL_ALREADY_EXISTS"));
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username":"shortuser",
                      "password":"short",
                      "displayName":"Short"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code")
                .value("PASSWORD_POLICY_VIOLATION"));

        jdbcTemplate.update(
            "update users set status = 'SUSPENDED' "
                + "where email = 'owner@example.com'"
        );
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username":"owneruser",
                      "password":"a safe long password"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code")
                .value("INVALID_CREDENTIALS"));
    }

    @Test
    void optionalEmailAcceptsValidFormatsAndIsNotRequired()
        throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username":"noemailuser",
                      "password":"a safe long password",
                      "displayName":"No email"
                    }
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username":"emailuser",
                      "email":"user+project@sub.example.co.kr",
                      "password":"a safe long password",
                      "displayName":"Valid domain"
                    }
                    """))
            .andExpect(status().isCreated());
    }

    @Test
    void refreshRotatesTokenAndLogoutRevokesCurrentToken() throws Exception {
        String signup = signup();
        String originalRefresh =
            JsonPath.read(signup, "$.data.tokens.refreshToken");
        String refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"%s"}
                    """.formatted(originalRefresh)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String access = JsonPath.read(refreshed, "$.data.accessToken");
        String rotatedRefresh =
            JsonPath.read(refreshed, "$.data.refreshToken");
        assertThat(rotatedRefresh).isNotEqualTo(originalRefresh);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"%s"}
                    """.formatted(originalRefresh)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code")
                .value("REFRESH_TOKEN_INVALID"));

        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"%s"}
                    """.formatted(rotatedRefresh)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"refreshToken":"%s"}
                    """.formatted(rotatedRefresh)))
            .andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from audit_events where metadata_json like '%"
                + originalRefresh + "%' or metadata_json like '%"
                + rotatedRefresh + "%'",
            Integer.class
        )).isZero();
    }

    @Test
    void updatesProfileAndRevokesExistingSessionsAfterPasswordChange() throws Exception {
        String session = signup();
        String access = JsonPath.read(session, "$.data.tokens.accessToken");
        String refresh = JsonPath.read(session, "$.data.tokens.refreshToken");

        mockMvc.perform(patch("/api/v1/users/me")
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName":"Updated owner",
                      "email":"updated@example.com",
                      "organizationName":"Venture Verify",
                      "departmentName":"Product",
                      "jobTitle":"Lead"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.displayName").value("Updated owner"))
            .andExpect(jsonPath("$.data.organizationName").value("Venture Verify"));

        mockMvc.perform(post("/api/v1/users/me/password")
                .header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "currentPassword":"a safe long password",
                      "newPassword":"a different safe passphrase"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(refresh)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"owneruser","password":"a different safe passphrase"}
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void protectedApiRejectsMissingAndInvalidTokenWithJsonWithoutSession()
        throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code")
                .value("AUTHENTICATION_REQUIRED"))
            .andExpect(header().doesNotExist("Set-Cookie"));

        mockMvc.perform(get("/api/v1/projects")
                .header("Authorization", "Bearer invalid.jwt.value"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code")
                .value("ACCESS_TOKEN_INVALID"))
            .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void productionProfileDoesNotAcceptDevelopmentUserHeader()
        throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                .header("X-User-Id", "1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredAndWronglySignedAccessTokens() throws Exception {
        String signup = signup();
        Number parsedUserId = JsonPath.read(signup, "$.data.user.id");
        Long userId = parsedUserId.longValue();
        Instant now = Instant.now();
        String expired = accessToken(
            "phase3-test-only-secret-key-at-least-32-bytes-long",
            userId,
            now.minusSeconds(120),
            now.minusSeconds(60)
        );
        String wrongSignature = accessToken(
            "different-test-secret-key-at-least-32-bytes-long",
            userId,
            now,
            now.plusSeconds(900)
        );

        for (String token : new String[] {expired, wrongSignature}) {
            mockMvc.perform(get("/api/v1/users/me")
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code")
                    .value("ACCESS_TOKEN_INVALID"));
        }
    }

    @Test
    void jwtPropertiesRejectMissingSecret() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            new JwtProperties(
                "issuer",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                "",
                Duration.ZERO
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private String signup() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username":"owneruser",
                      "email":"owner@example.com",
                      "password":"a safe long password",
                      "displayName":"Owner"
                    }
            """))
            .andExpect(status().isCreated());

        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username":"owneruser",
                      "password":"a safe long password"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private String accessToken(
        String secret,
        Long userId,
        Instant issuedAt,
        Instant expiresAt
    ) {
        var key = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
        );
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        var claims = JwtClaimsSet.builder()
            .issuer("aivle-security-test")
            .subject(userId.toString())
            .id(UUID.randomUUID().toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("tokenType", "ACCESS")
            .build();
        return encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(),
            claims
        )).getTokenValue();
    }
}
