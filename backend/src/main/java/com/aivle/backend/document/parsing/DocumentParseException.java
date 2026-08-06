package com.aivle.backend.document.parsing;

public class DocumentParseException extends RuntimeException {
    private final DocumentParseErrorCode errorCode;

    public DocumentParseException(DocumentParseErrorCode errorCode, String safeMessage) {
        super(safeMessage);
        this.errorCode = errorCode;
    }

    public DocumentParseException(
            DocumentParseErrorCode errorCode,
            String safeMessage,
            Throwable cause
    ) {
        super(safeMessage, cause);
        this.errorCode = errorCode;
    }

    public DocumentParseErrorCode getErrorCode() {
        return errorCode;
    }
}
