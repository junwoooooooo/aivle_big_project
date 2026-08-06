package com.aivle.backend.auth;

import com.aivle.backend.common.entity.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile({"test", "dev-header-auth"})
public class DevHeaderAuthenticationFilter extends OncePerRequestFilter {
    private final Environment environment;

    public DevHeaderAuthenticationFilter(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticateHeader(request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateHeader(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        String roleHeader = request.getHeader("X-User-Role");
        if (userId == null || userId.isBlank()) return;
        try {
            Long.valueOf(userId);
        } catch (NumberFormatException exception) {
            return;
        }

        if ((roleHeader == null || roleHeader.isBlank()) && environment.matchesProfiles("test")) {
            roleHeader = UserRole.USER.name();
        }
        if (roleHeader == null || roleHeader.isBlank()) return;

        UserRole role;
        try {
            role = UserRole.valueOf(roleHeader.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return;
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            userId.trim(),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
