package com.aivle.backend.auth.dto;

public record SignupResponse(UserResponse user, boolean signupCompleted) {
    public static SignupResponse from(UserResponse user) {
        return new SignupResponse(user, true);
    }
}
