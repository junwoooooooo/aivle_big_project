package com.aivle.backend.file.validation;

public record UploadedFileMetadata(
    String originalFilename,
    String contentType,
    long declaredSize
) {
}
