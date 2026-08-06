package com.aivle.backend.file.validation;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.config.FileStorageProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class BusinessPlanDocxPolicy implements UploadedFilePolicy {
    public static final String DOCX_MIME =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final long maxSizeBytes;

    public BusinessPlanDocxPolicy(FileStorageProperties properties) {
        this.maxSizeBytes = properties.businessPlanMaxSize().toBytes();
    }

    @Override
    public ValidatedUpload validate(UploadedFileMetadata metadata, InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new BusinessException(ErrorCode.FILE_REQUIRED);
        }
        String filename = normalizeFilename(metadata == null ? null : metadata.originalFilename());
        validateMediaType(metadata == null ? null : metadata.contentType());
        byte[] content = readAuthoritatively(inputStream);
        validateSignature(content);

        return ValidatedUpload.fromOwnedBytes(
            content,
            filename,
            "docx",
            DOCX_MIME,
            sha256(content)
        );
    }

    private String normalizeFilename(String filename) {
        if (filename == null) {
            throw new BusinessException(ErrorCode.FILE_NAME_INVALID);
        }
        String normalized = Normalizer.normalize(filename.trim(), Normalizer.Form.NFC);
        if (normalized.isBlank()
            || normalized.length() > 255
            || normalized.indexOf('/') >= 0
            || normalized.indexOf('\\') >= 0
            || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(ErrorCode.FILE_NAME_INVALID);
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".docx") || normalized.length() == 5) {
            throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED);
        }
        return normalized;
    }

    private void validateMediaType(String contentType) {
        if (!DOCX_MIME.equalsIgnoreCase(contentType == null ? "" : contentType.trim())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED);
        }
    }

    private byte[] readAuthoritatively(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > maxSizeBytes) {
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
            }
            output.write(buffer, 0, read);
        }
        if (total == 0) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        return output.toByteArray();
    }

    private void validateSignature(byte[] content) {
        boolean zipSignature = content.length >= 4
            && content[0] == 0x50
            && content[1] == 0x4b
            && content[2] == 0x03
            && content[3] == 0x04;
        if (!zipSignature) {
            throw new BusinessException(ErrorCode.FILE_SIGNATURE_INVALID);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
