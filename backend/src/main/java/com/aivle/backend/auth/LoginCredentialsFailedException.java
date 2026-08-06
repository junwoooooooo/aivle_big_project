package com.aivle.backend.auth;

public class LoginCredentialsFailedException extends RuntimeException {
    private final LoginAttemptRateLimiter.LoginAttemptStatus status;

    public LoginCredentialsFailedException(LoginAttemptRateLimiter.LoginAttemptStatus status) {
        this.status = status;
    }

    public LoginAttemptRateLimiter.LoginAttemptStatus getStatus() { return status; }
}
