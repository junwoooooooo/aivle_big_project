package com.aivle.backend.auth.dto;

public record TokenPairResponse(
    String tokenType,
    String accessToken,
    long accessTokenExpiresIn,
    String refreshToken,
    long refreshTokenExpiresIn
) {
}
