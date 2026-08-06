package com.aivle.backend.auth;

import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthSecurityErrorWriter {
    private final ObjectMapper objectMapper;

    public void write(
        HttpServletResponse response,
        ErrorCode code,
        String requestId
    ) throws IOException {
        response.setStatus(code.getHttpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(
            code.name(),
            code.getMessage(),
            List.of(),
            code.isRetryable(),
            requestId
        ));
    }
}
