package com.aivle.backend.file.validation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

public final class ValidatedUpload {
    private final byte[] content;
    private final String originalFilename;
    private final String extension;
    private final String contentType;
    private final String checksumSha256;

    public ValidatedUpload(
        byte[] content,
        String originalFilename,
        String extension,
        String contentType,
        String checksumSha256
    ) {
        this(
            Arrays.copyOf(content, content.length),
            originalFilename,
            extension,
            contentType,
            checksumSha256,
            true
        );
    }

    static ValidatedUpload fromOwnedBytes(
        byte[] content,
        String originalFilename,
        String extension,
        String contentType,
        String checksumSha256
    ) {
        return new ValidatedUpload(
            content,
            originalFilename,
            extension,
            contentType,
            checksumSha256,
            true
        );
    }

    private ValidatedUpload(
        byte[] content,
        String originalFilename,
        String extension,
        String contentType,
        String checksumSha256,
        boolean owned
    ) {
        this.content = content;
        this.originalFilename = originalFilename;
        this.extension = extension;
        this.contentType = contentType;
        this.checksumSha256 = checksumSha256;
    }

    public InputStream openStream() {
        return new ByteArrayInputStream(content);
    }

    public long sizeBytes() {
        return content.length;
    }

    public String originalFilename() {
        return originalFilename;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public String checksumSha256() {
        return checksumSha256;
    }
}
