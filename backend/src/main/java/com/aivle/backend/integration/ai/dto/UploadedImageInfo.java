package com.aivle.backend.integration.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UploadedImageInfo(
    @JsonProperty("original_filename")
    String originalFilename,
    @JsonProperty("content_type")
    String contentType,
    long size
) {
}
