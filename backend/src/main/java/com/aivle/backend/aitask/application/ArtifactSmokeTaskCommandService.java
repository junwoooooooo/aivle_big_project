package com.aivle.backend.aitask.application;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.aitask.dto.AiTaskStartResponse;
import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.common.entity.StorageType;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.document.application.IdempotencyKeyPolicy;
import com.aivle.backend.file.object.ObjectKeyGenerator;
import com.aivle.backend.file.object.ObjectStoragePort;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtifactSmokeTaskCommandService {
    public static final String SCHEMA_VERSION = "1.0";
    static final String CONTENT_TYPE = "application/json";
    private static final byte[] SOURCE = (
        "{\"message\":\"artifact-smoke\"}"
    ).getBytes(StandardCharsets.UTF_8);

    private final IdempotencyKeyPolicy idempotencyKeys;
    private final ServicePolicyService servicePolicy;
    private final ArtifactSmokeTaskTransactionService transaction;
    private final ObjectStoragePort objectStorage;
    private final ObjectKeyGenerator keyGenerator;

    public AiTaskStartResponse start(
        Long userId,
        Long projectId,
        String rawIdempotencyKey
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        String idempotencyKey = idempotencyKeys.normalize(
            rawIdempotencyKey
        );
        if (idempotencyKey == null) {
            throw new BusinessException(
                ErrorCode.IDEMPOTENCY_KEY_INVALID
            );
        }
        if (
            objectStorage.storageType()
            != StorageType.S3_COMPATIBLE
        ) {
            throw new BusinessException(
                ErrorCode.AI_OPERATIONS_NOT_AVAILABLE
            );
        }
        String fingerprint = fingerprint(projectId);
        var existing = transaction.findExisting(
            userId,
            projectId,
            idempotencyKey,
            fingerprint
        );
        if (existing.isPresent()) {
            return existing.get();
        }

        String objectKey = keyGenerator.aiArtifactJson();
        try {
            var stored = objectStorage.store(
                new ByteArrayInputStream(SOURCE),
                SOURCE.length,
                CONTENT_TYPE,
                objectKey
            );
            try {
                AiTaskStartResponse response = transaction.create(
                    userId,
                    projectId,
                    idempotencyKey,
                    fingerprint,
                    stored,
                    objectStorage.storageType()
                );
                if (!response.created()) {
                    cleanup(objectKey);
                }
                return response;
            } catch (RuntimeException exception) {
                cleanup(objectKey);
                throw exception;
            }
        } catch (IOException exception) {
            throw new BusinessException(
                ErrorCode.FILE_STORAGE_FAILED
            );
        }
    }

    private String fingerprint(Long projectId) {
        String source = JobType.SYSTEM_ARTIFACT_SMOKE_TEST.name()
            + ":" + SCHEMA_VERSION + ":" + projectId;
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    source.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                exception
            );
        }
    }

    private void cleanup(String objectKey) {
        try {
            objectStorage.delete(objectKey);
        } catch (IOException | RuntimeException exception) {
            log.error(
                "Failed to clean up uncommitted AI artifact",
                exception
            );
        }
    }
}
