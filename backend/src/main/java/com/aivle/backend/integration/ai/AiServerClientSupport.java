package com.aivle.backend.integration.ai;

import com.aivle.backend.integration.ai.dto.AiServerErrorResponse;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

@Component
public class AiServerClientSupport {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final AiServerProperties properties;
    private final ObjectMapper objectMapper;

    public AiServerClientSupport(
        AiServerProperties properties,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String resolveRequestId(String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            return requestId.trim();
        }
        return UUID.randomUUID().toString();
    }

    public void addHeaders(HttpHeaders headers, String requestId) {
        headers.set(REQUEST_ID_HEADER, requestId);
        if (properties.hasInternalApiKey()) {
            headers.set(
                INTERNAL_API_KEY_HEADER,
                properties.internalApiKey()
            );
        }
    }

    public <T> T execute(
        String requestId,
        Supplier<T> call
    ) {
        try {
            T response = call.get();
            if (response == null) {
                throw invalidResponse(
                    requestId,
                    "AI 서버 응답 본문이 없습니다.",
                    null
                );
            }
            return response;
        } catch (AiServerException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw remoteFailure(requestId, exception);
        } catch (ResourceAccessException exception) {
            throw new AiServerException(
                HttpStatus.GATEWAY_TIMEOUT.value(),
                "AI_SERVER_TIMEOUT",
                true,
                requestId,
                "AI 서버 응답 시간이 초과되었습니다.",
                exception
            );
        } catch (RestClientException exception) {
            if (isTimeout(exception)) {
                throw timeout(requestId, exception);
            }
            throw invalidResponse(
                requestId,
                "AI 서버 응답 형식이 올바르지 않습니다.",
                exception
            );
        }
    }

    private AiServerException remoteFailure(
        String fallbackRequestId,
        RestClientResponseException exception
    ) {
        int statusCode = exception.getStatusCode().value();
        AiServerErrorResponse envelope = parseError(exception);
        String requestId = fallbackRequestId;
        String errorCode = statusCode >= 500
            ? "AI_SERVER_INTERNAL_ERROR"
            : "AI_SERVER_REQUEST_REJECTED";
        String safeMessage = statusCode >= 500
            ? "AI 서버에서 요청을 처리하지 못했습니다."
            : "AI 서버가 요청을 거부했습니다.";

        if (envelope != null) {
            if (
                envelope.requestId() != null
                && !envelope.requestId().isBlank()
            ) {
                requestId = envelope.requestId();
            }
            if (envelope.error() != null) {
                if (
                    envelope.error().code() != null
                    && !envelope.error().code().isBlank()
                ) {
                    errorCode = envelope.error().code();
                }
                if (
                    envelope.error().message() != null
                    && !envelope.error().message().isBlank()
                    && statusCode < 500
                ) {
                    safeMessage = envelope.error().message();
                }
            }
        }

        return new AiServerException(
            statusCode,
            errorCode,
            statusCode >= 500,
            requestId,
            safeMessage,
            exception
        );
    }

    private AiServerErrorResponse parseError(
        RestClientResponseException exception
    ) {
        try {
            String body = exception.getResponseBodyAsString();
            if (body == null || body.isBlank()) {
                return null;
            }
            return objectMapper.readValue(
                body,
                AiServerErrorResponse.class
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (
                current instanceof SocketTimeoutException
                || current instanceof HttpTimeoutException
                || current instanceof TimeoutException
            ) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (
                    normalized.contains("timed out")
                    || normalized.contains("read timeout")
                ) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private AiServerException timeout(
        String requestId,
        Throwable cause
    ) {
        return new AiServerException(
            HttpStatus.GATEWAY_TIMEOUT.value(),
            "AI_SERVER_TIMEOUT",
            true,
            requestId,
            "AI server response timed out.",
            cause
        );
    }

    private AiServerException invalidResponse(
        String requestId,
        String safeMessage,
        Throwable cause
    ) {
        return new AiServerException(
            HttpStatus.BAD_GATEWAY.value(),
            "AI_SERVER_INVALID_RESPONSE",
            false,
            requestId,
            safeMessage,
            cause
        );
    }
}
