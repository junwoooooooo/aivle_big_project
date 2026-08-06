package com.aivle.backend.document.application;

public record DocumentParseJobRequest(
    Long projectId,
    Long documentId,
    Long documentVersionId
) {
}
