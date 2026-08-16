package com.aivle.backend.common.exception;

import com.aivle.backend.common.response.ApiResponse;
import java.util.List;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String safeMessage;
    private final List<ApiResponse.FieldError> fieldErrors;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BusinessException(ErrorCode errorCode, String safeMessage) {
        this(errorCode, safeMessage, List.of());
    }

    public BusinessException(ErrorCode errorCode, String safeMessage,
            List<ApiResponse.FieldError> fieldErrors) {
        super(safeMessage);
        this.errorCode = errorCode;
        this.safeMessage = safeMessage;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
}
