package com.aivle.backend.auth;

public class LoginRateLimitExceededException extends RuntimeException {
    private final LoginAttemptRateLimiter.LoginAttemptStatus status;

    public LoginRateLimitExceededException(LoginAttemptRateLimiter.LoginAttemptStatus status) {
        this.status = status;
    }

    public long getRetryAfterSeconds() {
        return status.retryAfterSeconds();
    }

    public LoginAttemptRateLimiter.LoginAttemptStatus getStatus() { return status; }
}
