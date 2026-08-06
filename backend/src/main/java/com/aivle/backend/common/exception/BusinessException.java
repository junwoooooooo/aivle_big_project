package com.aivle.backend.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String safeMessage;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public BusinessException(ErrorCode errorCode, String safeMessage) {
        super(safeMessage);
        this.errorCode = errorCode;
        this.safeMessage = safeMessage;
    }
}
