package com.aivle.backend.admin;

public enum ServiceSettingKey {
    REGISTRATION_ENABLED(
        "true",
        "Registration",
        "Allow new user registration"
    ),
    MAINTENANCE_MODE(
        "false",
        "Maintenance mode",
        "Pause non-admin writes while keeping read and administration access"
    );

    private final String defaultValue;
    private final String displayName;
    private final String description;

    ServiceSettingKey(String defaultValue, String displayName, String description) {
        this.defaultValue = defaultValue;
        this.displayName = displayName;
        this.description = description;
    }

    public String defaultValue() { return defaultValue; }
    public String displayName() { return displayName; }
    public String description() { return description; }
}
