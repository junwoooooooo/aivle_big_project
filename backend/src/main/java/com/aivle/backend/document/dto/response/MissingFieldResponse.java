package com.aivle.backend.document.dto.response;
import com.aivle.backend.common.entity.MissingFieldStatus;
public record MissingFieldResponse(Long id, String fieldCode, String label, boolean required,
                                   MissingFieldStatus status, String userValueJson) {}
