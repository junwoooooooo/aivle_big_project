package com.aivle.backend.auth;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptRateLimiter {
    public static final int MAX_FAILURES = 5;
    public static final int WARNING_START_FAILURES = 3;
    public static final Duration WINDOW = Duration.ofMinutes(5);
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptRateLimiter(Clock clock) { this.clock = clock; }

    public LoginAttemptStatus getStatus(String username, String ipAddress) {
        Instant now = clock.instant();
        Attempt usernameAttempt = activeAttempt("user:" + username, now);
        Attempt ipAttempt = activeAttempt("ip:" + ipAddress, now);
        int failedAttempts = Math.max(count(usernameAttempt), count(ipAttempt));
        long retryAfterSeconds = Math.max(remainingSeconds(usernameAttempt, now), remainingSeconds(ipAttempt, now));
        boolean limited = failedAttempts >= MAX_FAILURES && retryAfterSeconds > 0;
        int remainingAttempts = Math.max(0, MAX_FAILURES - failedAttempts);
        WarningLevel warningLevel = limited
            ? WarningLevel.LIMITED
            : failedAttempts >= MAX_FAILURES - 1
                ? WarningLevel.FINAL_WARNING
                : failedAttempts >= WARNING_START_FAILURES
                    ? WarningLevel.CAUTION
                    : WarningLevel.NONE;
        return new LoginAttemptStatus(failedAttempts, remainingAttempts, warningLevel, limited, retryAfterSeconds);
    }

    public LoginAttemptStatus recordFailure(String username, String ipAddress) {
        Instant now = clock.instant();
        record("user:" + username, now); record("ip:" + ipAddress, now);
        return getStatus(username, ipAddress);
    }

    public void recordSuccess(String username, String ipAddress) {
        attempts.remove("user:" + username); attempts.remove("ip:" + ipAddress);
    }

    private Attempt activeAttempt(String key, Instant now) {
        Attempt attempt = attempts.get(key);
        return attempt != null && now.isBefore(attempt.startedAt.plus(WINDOW)) ? attempt : null;
    }
    private int count(Attempt attempt) { return attempt == null ? 0 : attempt.count; }
    private long remainingSeconds(Attempt attempt, Instant now) {
        if (attempt == null || attempt.count < MAX_FAILURES) return 0;
        long seconds = Duration.between(now, attempt.startedAt.plus(WINDOW)).toSeconds();
        return Math.max(0, seconds);
    }
    private void record(String key, Instant now) { attempts.compute(key, (ignored, current) -> current == null || !now.isBefore(current.startedAt.plus(WINDOW)) ? new Attempt(now, 1) : new Attempt(current.startedAt, current.count + 1)); }
    private record Attempt(Instant startedAt, int count) { }
    public enum WarningLevel { NONE, CAUTION, FINAL_WARNING, LIMITED }
    public record LoginAttemptStatus(int failedAttempts, int remainingAttempts, WarningLevel warningLevel,
                                     boolean limited, long retryAfterSeconds) { }
}
