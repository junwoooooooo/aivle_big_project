package com.aivle.backend.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap-admin")
public record BootstrapAdminProperties(boolean enabled, String username, String email, String password) {
}
