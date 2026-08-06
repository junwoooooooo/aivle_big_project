package com.aivle.backend.document.application;

import com.aivle.backend.common.entity.DocumentType;
import com.aivle.backend.file.validation.ValidatedUpload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class DocumentRequestFingerprint {
    public String calculate(Long projectId, DocumentType documentType, ValidatedUpload upload) {
        MessageDigest digest = sha256();
        update(digest, String.valueOf(projectId));
        update(digest, documentType.name());
        update(digest, upload.checksumSha256());
        update(digest, upload.originalFilename());
        update(digest, String.valueOf(upload.sizeBytes()));
        update(digest, upload.contentType().toLowerCase());
        return HexFormat.of().formatHex(digest.digest());
    }

    private void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
        digest.update((byte) '|');
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
