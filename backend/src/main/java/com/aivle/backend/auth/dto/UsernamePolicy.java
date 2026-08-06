package com.aivle.backend.auth.dto;

import java.util.Set;

public final class UsernamePolicy {
    public static final String FORMAT = "^[a-z0-9][a-z0-9._-]{3,29}$";
    public static final Set<String> RESERVED = Set.of("admin", "administrator", "root", "system", "support", "help", "api", "auth", "login", "signup", "user", "users", "project", "projects", "me", "null", "undefined", "ventureverify", "venture-verify");
    private UsernamePolicy() { }
}
