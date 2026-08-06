package com.aivle.backend.auth;

import com.aivle.backend.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfiguration {
    private final AuthSecurityErrorWriter errorWriter;

    @Bean
    @Profile("!test & !dev-header-auth")
    SecurityFilterChain authenticatedSecurityFilterChain(
        HttpSecurity http,
        @Qualifier("accessTokenDecoder") JwtDecoder accessTokenDecoder
    ) throws Exception {
        return http
            .csrf(csrf -> csrf
                .disable()
            )
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/v1/service-policy").permitAll()
                .requestMatchers(
                    "/api/v1/auth/signup",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/actuator/health/**",
                    "/h2-console/**"
                ).permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.decoder(accessTokenDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint((request, response, exception) ->
                    errorWriter.write(
                        response,
                        ErrorCode.ACCESS_TOKEN_INVALID,
                        requestId(request)
                    )))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) ->
                    errorWriter.write(
                        response,
                        ErrorCode.AUTHENTICATION_REQUIRED,
                        requestId(request)
                    ))
                .accessDeniedHandler((request, response, exception) ->
                    errorWriter.write(
                        response,
                        ErrorCode.ACCESS_DENIED,
                        requestId(request)
                    )))
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
            )
            .build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    @Profile("test")
    SecurityFilterChain testSecurityFilterChain(
        HttpSecurity http,
        DevHeaderAuthenticationFilter headerAuthenticationFilter
    ) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/v1/service-policy").permitAll()
                .requestMatchers(
                    "/api/v1/auth/signup",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/actuator/health/**",
                    "/h2-console/**"
                ).permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(headerAuthenticationFilter, AnonymousAuthenticationFilter.class)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) ->
                    errorWriter.write(response, ErrorCode.AUTHENTICATION_REQUIRED, requestId(request)))
                .accessDeniedHandler((request, response, exception) ->
                    errorWriter.write(response, ErrorCode.ACCESS_DENIED, requestId(request))))
            .build();
    }

    @Bean
    @Profile("dev-header-auth")
    SecurityFilterChain developmentSecurityFilterChain(
        HttpSecurity http,
        DevHeaderAuthenticationFilter headerAuthenticationFilter
    ) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/v1/service-policy").permitAll()
                .requestMatchers(
                    "/api/v1/auth/signup",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/actuator/health/**",
                    "/h2-console/**"
                ).permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(headerAuthenticationFilter, AnonymousAuthenticationFilter.class)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) ->
                    errorWriter.write(response, ErrorCode.AUTHENTICATION_REQUIRED, requestId(request)))
                .accessDeniedHandler((request, response, exception) ->
                    errorWriter.write(response, ErrorCode.ACCESS_DENIED, requestId(request))))
            .build();
    }

    private String requestId(HttpServletRequest request) {
        return request.getHeader("X-Request-Id");
    }
}
