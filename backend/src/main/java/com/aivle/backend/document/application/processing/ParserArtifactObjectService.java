package com.aivle.backend.document.application.processing;

import com.aivle.backend.file.object.ObjectKeyGenerator;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.job.runner.JobProcessingException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParserArtifactObjectService {
    public static final String CONTENT_TYPE = "application/json";

    private final ObjectStoragePort storage;
    private final ObjectKeyGenerator keys;

    public StoredParserArtifact store(
        DocumentJobContext context,
        String parserVersion,
        ParserArtifactPayload payload
    ) {
        String finalKey = keys.parserArtifact(
            context.projectId(),
            context.documentId(),
            context.documentVersionId(),
            parserVersion,
            payload.checksumSha256()
        );
        if (storage.exists(finalKey)) {
            verifyExisting(finalKey, payload);
            return descriptor(finalKey, payload, false);
        }

        String temporaryKey = keys.parserArtifactTemporary(
            context.projectId(),
            context.documentId(),
            context.documentVersionId()
        );
        try {
            putAndVerify(temporaryKey, payload);
            putAndVerify(finalKey, payload);
            return descriptor(finalKey, payload, true);
        } catch (IOException | RuntimeException exception) {
            // The final key is content-addressed. Do not delete it here:
            // another worker may have won the conditional create race.
            // An incomplete/unreferenced final object is handled by orphan
            // reconciliation.
            throw JobProcessingException.nonRetryable(
                "PARSER_ARTIFACT_STORAGE_FAILED",
                "파서 artifact를 저장할 수 없습니다.",
                exception
            );
        } finally {
            deleteBestEffort(temporaryKey);
        }
    }

    public void deleteBestEffort(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (IOException | RuntimeException exception) {
            log.error(
                "Failed to clean parser artifact object {}",
                storageKey,
                exception
            );
        }
    }

    private void putAndVerify(
        String objectKey,
        ParserArtifactPayload payload
    ) throws IOException {
        byte[] bytes = payload.bytes();
        ObjectStoragePort.StoredObject stored = storage.store(
            new ByteArrayInputStream(bytes),
            bytes.length,
            CONTENT_TYPE,
            objectKey
        );
        ObjectStoragePort.ObjectMetadata metadata =
            storage.metadata(objectKey);
        if (stored.sizeBytes() != bytes.length
            || metadata.sizeBytes() != bytes.length
            || !payload.checksumSha256().equals(
                stored.checksumSha256()
            )
            || !CONTENT_TYPE.equals(stored.contentType())
            || !CONTENT_TYPE.equals(metadata.contentType())) {
            throw new IOException(
                "parser artifact object integrity mismatch"
            );
        }
    }

    private void verifyExisting(
        String objectKey,
        ParserArtifactPayload payload
    ) {
        try (
            InputStream input = storage.open(objectKey)
        ) {
            ObjectStoragePort.ObjectMetadata metadata =
                storage.metadata(objectKey);
            String checksum = sha256(input);
            if (metadata.sizeBytes() != payload.bytes().length
                || !payload.checksumSha256().equals(checksum)) {
                throw new IOException(
                    "existing parser artifact integrity mismatch"
                );
            }
        } catch (IOException | RuntimeException exception) {
            throw JobProcessingException.nonRetryable(
                "PARSER_ARTIFACT_INTEGRITY_FAILED",
                "기존 파서 artifact 무결성을 확인할 수 없습니다.",
                exception
            );
        }
    }

    private StoredParserArtifact descriptor(
        String objectKey,
        ParserArtifactPayload payload,
        boolean created
    ) {
        return new StoredParserArtifact(
            objectKey,
            Path.of(objectKey).getFileName().toString(),
            payload.bytes().length,
            payload.checksumSha256(),
            payload.schemaVersion(),
            payload.blockCount(),
            created
        );
    }

    private String sha256(InputStream input) throws IOException {
        try {
            MessageDigest digest =
                MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                exception
            );
        }
    }
}
