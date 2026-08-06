package com.aivle.backend.auth.dto;

public record AuthResponse(UserResponse user, TokenPairResponse tokens) {
}
