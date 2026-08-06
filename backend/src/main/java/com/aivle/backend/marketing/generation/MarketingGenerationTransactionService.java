package com.aivle.backend.marketing.generation;

import com.aivle.backend.aitask.entity.AiTaskArtifact;
import com.aivle.backend.aitask.repository.AiTaskArtifactRepository;
import com.aivle.backend.common.entity.JobType;
import com.aivle.backend.common.entity.StorageType;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.entity.StoredFile;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.file.repository.StoredFileRepository;
import com.aivle.backend.job.entity.AnalysisJob;
import com.aivle.backend.job.repository.AnalysisJobRepository;
import com.aivle.backend.marketing.content.MarketingContentRepository;
import com.aivle.backend.marketing.content.MarketingContentVersion;
import com.aivle.backend.marketing.content.MarketingContentVersionRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MarketingGenerationTransactionService {
    private final MarketingContentRepository contents;
    private final ProjectRepository projects;
    private final MarketingContentVersionRepository versions;
    private final AnalysisJobRepository jobs;
    private final StoredFileRepository storedFiles;
    private final AiTaskArtifactRepository artifacts;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SourceSnapshot source(
        Long userId, Long projectId, Long contentId, Long sourceVersionId
    ) {
        var content = contents.findByIdAndProjectIdAndDeletedAtIsNull(
            contentId, projectId
        ).filter(value -> value.getProject().getOwner().getId().equals(userId))
            .orElseThrow(() -> new BusinessException(
                ErrorCode.MARKETING_CONTENT_NOT_FOUND));
        MarketingContentVersion version = sourceVersionId == null
            ? versions.findByMarketingContentIdAndVersionNumber(
                contentId, content.getCurrentVersion()).orElseThrow()
            : versions.findByIdAndMarketingContentId(sourceVersionId, contentId)
                .orElseThrow(() -> new BusinessException(
                    ErrorCode.MARKETING_CONTENT_NOT_FOUND));
        return SourceSnapshot.from(content.getId(), projectId, version);
    }

    @Transactional(readOnly = true)
    public Optional<MarketingGenerationStartResponse> existing(
        Long userId, Long projectId, String idempotencyKey, String fingerprint
    ) {
        requireOwnedProject(userId, projectId);
        return jobs.findByProjectIdAndJobTypeAndIdempotencyKeyAndDeletedAtIsNull(
            projectId, JobType.MARKETING_GENERATION, idempotencyKey
        ).map(job -> response(job, fingerprint, false));
    }

    @Transactional(readOnly = true)
    public RerunSource rerunSource(
        Long userId, Long projectId, Long contentId, Long originalJobId
    ) {
        AnalysisJob job = jobs
            .findByIdAndProjectIdAndProjectOwnerIdAndJobTypeAndDeletedAtIsNull(
                originalJobId, projectId, userId, JobType.MARKETING_GENERATION)
            .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
        var version = versions.findByAnalysisJobId(originalJobId)
            .filter(value -> value.getMarketingContent().getId().equals(contentId))
            .orElseThrow(() -> new BusinessException(
                ErrorCode.MARKETING_CONTENT_NOT_FOUND));
        var source = artifacts.findByJobIdAndRole(
            originalJobId,
            com.aivle.backend.aitask.entity.AiArtifactRole.SOURCE
        ).orElseThrow(() -> new BusinessException(
            ErrorCode.MARKETING_ASSET_INVALID));
        return new RerunSource(
            SourceSnapshot.from(contentId, projectId, version),
            source.getStoredFile(),
            job
        );
    }

    @Transactional
    public MarketingGenerationStartResponse create(
        Long userId,
        SourceSnapshot source,
        String idempotencyKey,
        String fingerprint,
        String requestJson,
        ObjectStoragePort.StoredObject stored,
        StorageType storageType,
        String originalFilename,
        String extension,
        AnalysisJob rerunOf
    ) {
        var content = contents.findForUpdateByIdAndProjectIdAndDeletedAtIsNull(
            source.contentId(), source.projectId()
        ).filter(value -> value.getProject().getOwner().getId().equals(userId))
            .orElseThrow(() -> new BusinessException(
                ErrorCode.MARKETING_CONTENT_NOT_FOUND));
        var existing = jobs
            .findByProjectIdAndJobTypeAndIdempotencyKeyAndDeletedAtIsNull(
                source.projectId(), JobType.MARKETING_GENERATION, idempotencyKey);
        if (existing.isPresent()) {
            return response(existing.get(), fingerprint, false);
        }
        StoredFile file = storedFiles.save(StoredFile.available(
            storageType, stored.objectKey(), originalFilename,
            filename(stored.objectKey()), extension, stored.contentType(),
            stored.sizeBytes(), stored.checksumSha256()
        ));
        AnalysisJob job = jobs.save(AnalysisJob.queuedMarketingGeneration(
            content.getProject(), requestJson, idempotencyKey, fingerprint, rerunOf
        ));
        artifacts.save(AiTaskArtifact.source(job, file));
        events.publishEvent(new MarketingGenerationRequested(job.getId()));
        return new MarketingGenerationStartResponse(
            job.getId(), job.getStatus(), source.contentId(),
            source.versionId(), true,
            rerunOf == null ? null : rerunOf.getId()
        );
    }

    private void requireOwnedProject(Long userId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private MarketingGenerationStartResponse response(
        AnalysisJob job, String fingerprint, boolean created
    ) {
        if (!job.hasSameIdempotentRequest(fingerprint)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        SourceSnapshot source = sourceFromJson(job.getRequestJson());
        return new MarketingGenerationStartResponse(
            job.getId(), job.getStatus(), source.contentId(),
            source.versionId(), created,
            job.getRerunOfJob() == null ? null : job.getRerunOfJob().getId()
        );
    }

    private String filename(String key) {
        return key.substring(key.lastIndexOf('/') + 1);
    }

    private SourceSnapshot sourceFromJson(String json) {
        try {
            var node = objectMapper.readTree(json);
            var input = node.get("input");
            return new SourceSnapshot(
                node.get("contentId").asLong(),
                node.get("projectId").asLong(),
                node.get("sourceVersionId").asLong(),
                input.get("promotion_name").asText(),
                input.get("main_banner").asText(),
                input.get("supporting_copy").asText(),
                input.get("mood").asText(),
                input.get("banner_format").asText()
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                "marketing job snapshot is invalid", exception);
        }
    }

    public record SourceSnapshot(
        Long contentId,
        Long projectId,
        Long versionId,
        String promotionName,
        String mainBanner,
        String supportingCopy,
        String mood,
        String bannerFormat
    ) {
        static SourceSnapshot from(
            Long contentId, Long projectId, MarketingContentVersion version
        ) {
            String supporting = first(
                version.getSubheadline(),
                version.getSupportingText(),
                version.getBodyCopy(),
                "Marketing banner"
            );
            return new SourceSnapshot(
                contentId, projectId, version.getId(),
                limit(version.getMarketingContent().getTitle(), 100),
                limit(version.getHeadline(), 80),
                limit(supporting, 150),
                mood(version.getVisualStyle().name()),
                format(version.getMarketingContent().getFormat().name())
            );
        }

        private static String first(String... values) {
            for (String value : values) {
                if (value != null && !value.isBlank()) return value;
            }
            return "Marketing banner";
        }
        private static String limit(String value, int max) {
            String normalized = value == null ? "" : value.trim();
            return normalized.length() <= max
                ? normalized : normalized.substring(0, max);
        }
        private static String mood(String tone) {
            return switch (tone) {
                case "TRUSTWORTHY" -> "TRUSTWORTHY";
                case "FRIENDLY", "ENERGETIC" -> "BRIGHT_FRIENDLY";
                case "EMOTIONAL" -> "EMOTIONAL";
                case "PROFESSIONAL" -> "PROFESSIONAL";
                case "PREMIUM" -> "LUXURIOUS";
                case "MINIMAL" -> "MINIMAL";
                default -> "BOLD";
            };
        }
        private static String format(String value) {
            if (value.startsWith("SQUARE")) return "SQUARE";
            if (value.startsWith("PORTRAIT")
                || value.startsWith("STORY")
                || value.startsWith("A4")) return "PORTRAIT";
            return "LANDSCAPE";
        }
    }

    public record RerunSource(
        SourceSnapshot snapshot,
        StoredFile storedFile,
        AnalysisJob originalJob
    ) {
    }
}
