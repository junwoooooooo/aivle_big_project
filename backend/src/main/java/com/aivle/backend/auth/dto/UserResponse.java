package com.aivle.backend.auth.dto;

import com.aivle.backend.user.entity.User;

public record UserResponse(
    Long id,
    String username,
    String email,
    String displayName,
    String organizationName,
    String departmentName,
    String jobTitle,
    String role,
    String status,
    String accountStatus
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getName(),
            user.getOrganizationName(),
            user.getDepartmentName(),
            user.getJobTitle(),
            user.getRole().name(),
            user.getStatus().name(),
            user.getStatus().name()
        );
    }
}
