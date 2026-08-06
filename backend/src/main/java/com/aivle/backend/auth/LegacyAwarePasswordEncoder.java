package com.aivle.backend.auth;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class LegacyAwarePasswordEncoder implements PasswordEncoder {
    private final PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final PasswordEncoder bcrypt = new BCryptPasswordEncoder(10);

    @Override public String encode(CharSequence rawPassword) { return "{argon2}" + argon2.encode(rawPassword); }
    @Override public boolean matches(CharSequence rawPassword, String encodedPassword) { return encodedPassword.startsWith("$2") ? bcrypt.matches(rawPassword, encodedPassword) : encodedPassword.startsWith("{argon2}") && argon2.matches(rawPassword, encodedPassword.substring("{argon2}".length())); }
    @Override public boolean upgradeEncoding(String encodedPassword) { return encodedPassword.startsWith("$2"); }
}
