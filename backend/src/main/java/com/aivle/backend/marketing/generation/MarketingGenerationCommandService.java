package com.aivle.backend.marketing.generation;

import com.aivle.backend.admin.ServicePolicyService;
import com.aivle.backend.common.entity.StorageType;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.document.application.IdempotencyKeyPolicy;
import com.aivle.backend.file.config.FileStorageProperties;
import com.aivle.backend.file.object.ObjectKeyGenerator;
import com.aivle.backend.file.object.ObjectStoragePort;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingGenerationCommandService {
    public static final String SCHEMA_VERSION = "1.0";
    private static final Map<String, String> IMAGE_TYPES = Map.of(
        "png", "image/png",
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg",
        "webp", "image/webp"
    );

    private final IdempotencyKeyPolicy idempotencyKeys;
    private final ServicePolicyService servicePolicy;
    private final MarketingGenerationTransactionService transaction;
    private final ObjectStoragePort storage;
    private final ObjectKeyGenerator keys;
    private final FileStorageProperties fileProperties;
    private final ObjectMapper objectMapper;

    public MarketingGenerationStartResponse start(
        Long userId,
        Long projectId,
        Long contentId,
        Long sourceVersionId,
        String rawIdempotencyKey,
        MultipartFile image
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        requireS3();
        String idempotencyKey = requireIdempotency(rawIdempotencyKey);
        ImageUpload upload = validate(image);
        var source = transaction.source(
            userId, projectId, contentId, sourceVersionId);
        String fingerprint = fingerprint(
            source, upload.checksumSha256());
        var existing = transaction.existing(
            userId, projectId, idempotencyKey, fingerprint);
        if (existing.isPresent()) return existing.get();
        String key = keys.aiArtifactImage(upload.extension());
        try {
            var stored = storage.store(
                image.getInputStream(), image.getSize(),
                upload.contentType(), key);
            try {
                var response = transaction.create(
                    userId, source, idempotencyKey, fingerprint,
                    requestJson(source), stored, storage.storageType(),
                    image.getOriginalFilename(), upload.extension(), null);
                if (!response.created()) cleanup(key);
                return response;
            } catch (RuntimeException exception) {
                cleanup(key);
                throw exception;
            }
        } catch (IOException exception) {
            cleanup(key);
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    public MarketingGenerationStartResponse rerun(
        Long userId,
        Long projectId,
        Long contentId,
        Long originalJobId,
        String rawIdempotencyKey
    ) {
        servicePolicy.requireWriteAvailableForUser(userId);
        requireS3();
        String idempotencyKey = requireIdempotency(rawIdempotencyKey);
        var rerun = transaction.rerunSource(
            userId, projectId, contentId, originalJobId);
        String fingerprint = fingerprint(
            rerun.snapshot(),
            rerun.storedFile().getChecksumSha256());
        var existing = transaction.existing(
            userId, projectId, idempotencyKey, fingerprint);
        if (existing.isPresent()) return existing.get();
        String extension = rerun.storedFile().getExtension();
        String key = keys.aiArtifactImage(extension);
        try (
            InputStream input = storage.open(
                rerun.storedFile().getStorageKey())
        ) {
            var stored = storage.store(
                input, rerun.storedFile().getSizeBytes(),
                rerun.storedFile().getMimeType(), key);
            try {
                var response = transaction.create(
                    userId, rerun.snapshot(), idempotencyKey,
                    fingerprint, requestJson(rerun.snapshot()), stored,
                    storage.storageType(),
                    rerun.storedFile().getOriginalFilename(),
                    extension, rerun.originalJob());
                if (!response.created()) cleanup(key);
                return response;
            } catch (RuntimeException exception) {
                cleanup(key);
                throw exception;
            }
        } catch (IOException exception) {
            cleanup(key);
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    private ImageUpload validate(MultipartFile image) {
        if (image == null) throw new BusinessException(ErrorCode.FILE_REQUIRED);
        if (image.isEmpty()) throw new BusinessException(ErrorCode.FILE_EMPTY);
        if (image.getSize() > fileProperties.imageMaxSize().toBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String name = image.getOriginalFilename();
        if (name == null || name.isBlank() || name.contains("/")
            || name.contains("\\")) {
            throw new BusinessException(ErrorCode.FILE_NAME_INVALID);
        }
        int dot = name.lastIndexOf('.');
        String extension = dot < 0
            ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String expectedType = IMAGE_TYPES.get(extension);
        if (expectedType == null
            || !expectedType.equalsIgnoreCase(image.getContentType())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED);
        }
        try (InputStream input = image.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return new ImageUpload(
                extension, expectedType,
                HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
        }
    }

    private String requestJson(
        MarketingGenerationTransactionService.SourceSnapshot source
    ) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "projectId", source.projectId(),
                "contentId", source.contentId(),
                "sourceVersionId", source.versionId(),
                "schemaVersion", SCHEMA_VERSION,
                "input", Map.of(
                    "promotion_name", source.promotionName(),
                    "main_banner", source.mainBanner(),
                    "supporting_copy", source.supportingCopy(),
                    "mood", source.mood(),
                    "banner_format", source.bannerFormat(),
                    "emphasis_keywords", java.util.List.of()
                )
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                "marketing request snapshot serialization failed", exception);
        }
    }

    private String fingerprint(
        MarketingGenerationTransactionService.SourceSnapshot source,
        String checksum
    ) {
        String value = "MARKETING_GENERATION:" + SCHEMA_VERSION + ":"
            + source.projectId() + ":" + source.contentId() + ":"
            + source.versionId() + ":" + source.promotionName() + ":"
            + source.mainBanner() + ":" + source.supportingCopy() + ":"
            + source.mood() + ":" + source.bannerFormat() + ":" + checksum;
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String requireIdempotency(String raw) {
        String value = idempotencyKeys.normalize(raw);
        if (value == null) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
        return value;
    }

    private void requireS3() {
        if (storage.storageType() != StorageType.S3_COMPATIBLE) {
            throw new BusinessException(ErrorCode.AI_OPERATIONS_NOT_AVAILABLE);
        }
    }

    private void cleanup(String key) {
        try {
            storage.delete(key);
        } catch (IOException | RuntimeException exception) {
            log.error("Failed to clean up marketing artifact", exception);
        }
    }

    private record ImageUpload(
        String extension,
        String contentType,
        String checksumSha256
    ) {
    }
}
