package com.aivle.backend.pipeline.marketing.application;

import static com.aivle.backend.pipeline.marketing.api.MarketingApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.pipeline.marketing.domain.*;
import com.aivle.backend.pipeline.marketing.repository.*;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class MarketingContentService {
    public static final String REQUEST_CONTRACT = "marketing-content-request-v1";
    private final ProjectRepository projects;
    private final ProjectEvidenceArtifactService evidenceArtifacts;
    private final ObjectStoragePort objectStorage;
    private final MarketingSourceSnapshotService sourceSnapshots;
    private final MarketingContentRepository contents;
    private final MarketingContentRevisionRepository revisions;
    private final MarketingAssetRepository assets;
    private final MarketingResultContract results;
    private final MarketingLegalGuard legalGuard;
    private final TaskRunService taskRuns;
    private final CanonicalInputHasher inputHasher;
    private final JobEventPublisher events;
    private final ObjectMapper mapper;

    @Transactional
    public ContentView create(Long ownerId, Long projectId, CreateRequest request, String idempotencyKey, String correlationId) {
        requireOwned(ownerId, projectId); validateRequest(request);
        if (request.referenceArtifactId() != null && !request.referenceArtifactId().isBlank()) {
            var artifact = evidenceArtifacts.requireReferenceable(ownerId, projectId, request.referenceArtifactId());
            if (!("image/png".equals(artifact.getMediaType()) || "image/jpeg".equals(artifact.getMediaType()))
                    || artifact.getSizeBytes() <= 0 || artifact.getSizeBytes() > 20L * 1024 * 1024) {
                throw new BusinessException(ErrorCode.MARKETING_ASSET_INVALID);
            }
        }
        MarketingSourceSnapshot source = sourceSnapshots.requireCurrent(projectId);
        if (!source.getId().equals(request.marketingSourceSnapshotId())) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        String sourceJson = source.getSnapshotJson(); String requestJson = mapper.writeValueAsString(request);
        String id = UUID.randomUUID().toString();
        String title = mapper.readTree(sourceJson).path("conceptName").asText("Marketing") + " - " + request.contentType().name();
        MarketingContent content = contents.save(MarketingContent.queued(id, projectId, source.getId(),
            source.getSnapshotHash(), sourceJson, requestJson, request.contentType(), request.channel(), title, ownerId));
        String taskId = enqueue(ownerId, projectId, content, requestJson, sourceJson, idempotencyKey, correlationId);
        content.attachTaskRun(taskId);
        publish(projectId, taskId, "QUEUED", "job.marketing.queued", JobEvent.Status.QUEUED, null);
        return view(content, source);
    }

    @Transactional(readOnly = true)
    public ContentListView list(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId); MarketingSourceSnapshot current = sourceSnapshots.findCurrent(projectId);
        return new ContentListView(contents.findAllByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)
            .stream().map(content -> summary(content, current)).toList());
    }
    @Transactional(readOnly = true)
    public ContentView get(Long ownerId, Long projectId, String id) {
        requireOwned(ownerId, projectId); return view(find(projectId, id), sourceSnapshots.findCurrent(projectId));
    }

    @Transactional
    public ContentView edit(Long ownerId, Long projectId, String id, EditRequest request) {
        requireOwned(ownerId, projectId); MarketingContent content = findLocked(projectId, id);
        if (!Set.of(MarketingRevisionType.TONE_EDITED, MarketingRevisionType.SHORTENED,
                MarketingRevisionType.LEGAL_NOTICE_APPLIED, MarketingRevisionType.USER_EDITED).contains(request.revisionType()))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        try {
            results.validate(request.result(), content.getContentType());
            legalGuard.validate(content.getSourceSnapshotJson(), request.result());
            int number = content.addUserRevision();
            revisions.save(MarketingContentRevision.create(id, number, request.revisionType(),
                MarketingRevisionOrigin.USER, mapper.writeValueAsString(request.result()), ownerId));
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, invalid.getMessage());
        }
        return view(content, sourceSnapshots.findCurrent(projectId));
    }

    @Transactional
    public ContentView regenerate(Long ownerId, Long projectId, String id, String idempotencyKey, String correlationId) {
        requireOwned(ownerId, projectId); MarketingContent content = findLocked(projectId, id);
        MarketingSourceSnapshot source = sourceSnapshots.requireCurrent(projectId);
        ObjectNode request = (ObjectNode) mapper.readTree(content.getRequestJson());
        request.put("marketingSourceSnapshotId", source.getId());
        String requestJson = mapper.writeValueAsString(request); String sourceJson = source.getSnapshotJson();
        String taskId = enqueue(ownerId, projectId, content, requestJson, sourceJson, idempotencyKey, correlationId);
        try { content.regenerate(source.getId(), source.getSnapshotHash(), sourceJson, requestJson, taskId); }
        catch (IllegalStateException invalid) { throw new BusinessException(ErrorCode.INVALID_REQUEST, invalid.getMessage()); }
        publish(projectId, taskId, "QUEUED", "job.marketing.queued", JobEvent.Status.QUEUED, null);
        return view(content, source);
    }

    @Transactional
    public ContentView finalizeContent(Long ownerId, Long projectId, String id) {
        requireOwned(ownerId, projectId); MarketingContent content = findLocked(projectId, id);
        MarketingSourceSnapshot current = sourceSnapshots.requireCurrent(projectId);
        if (stale(content, current)) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        MarketingContentRevision latest = revisions.findFirstByContentIdAndDeletedAtIsNullOrderByRevisionNumberDesc(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        try {
            var result = mapper.readTree(latest.getResultJson()); legalGuard.validate(content.getSourceSnapshotJson(), result);
            int number = content.finalizeContent(Instant.now());
            revisions.save(MarketingContentRevision.create(id, number, MarketingRevisionType.FINALIZED,
                MarketingRevisionOrigin.SYSTEM, latest.getResultJson(), ownerId));
        } catch (IllegalStateException invalid) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, invalid.getMessage());
        }
        return view(content, current);
    }

    private String enqueue(Long ownerId, Long projectId, MarketingContent content, String requestJson,
            String sourceJson, String key, String correlation) {
        if (key == null || key.isBlank()) throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        ObjectNode input = mapper.createObjectNode(); input.set("source", mapper.readTree(sourceJson));
        input.set("request", mapper.readTree(requestJson));
        String json = mapper.writeValueAsString(input);
        String hash = inputHasher.hash(TaskType.MARKETING_CONTENT_GENERATION, "1.0", "ko-KR", json);
        return taskRuns.create(ownerId, projectId, TaskType.MARKETING_CONTENT_GENERATION, "MARKETING_CONTENT",
            content.getId(), json, hash, key, correlation == null || correlation.isBlank()
                ? UUID.randomUUID().toString() : correlation, 2).getId();
    }
    private ContentView view(MarketingContent content, MarketingSourceSnapshot current) {
        return new ContentView(summary(content, current), mapper.readTree(content.getSourceSnapshotJson()),
            mapper.readTree(content.getRequestJson()), revisions.findAllByContentIdAndDeletedAtIsNullOrderByRevisionNumberAsc(content.getId())
                .stream().map(revision -> new RevisionView(revision.getId(), revision.getRevisionNumber(),
                    revision.getRevisionType(), revision.getOrigin(), mapper.readTree(revision.getResultJson()))).toList(),
            assets.findAllByContentIdAndDeletedAtIsNullOrderByCreatedAtAsc(content.getId()).stream()
                .map(MarketingAsset::getArtifactRef).map(this::browserArtifactUrl).toList());
    }
    private ContentSummary summary(MarketingContent content, MarketingSourceSnapshot current) {
        String status = stale(content, current) ? "STALE" : content.getStatus().name();
        String activeJobId = ("QUEUED".equals(status) || "RUNNING".equals(status)) ? content.getTaskRunId() : null;
        return new ContentSummary(content.getId(), content.getMarketingSourceSnapshotId(), content.getSourceSnapshotHash(),
            content.getContentType(), content.getChannel(), content.getTitle(), status, content.getCurrentRevisionNumber(),
            content.getTaskRunId(), activeJobId, content.getMarketingSourceSnapshotId(), content.getUpdatedAt(), content.getFinalizedAt());
    }
    private boolean stale(MarketingContent content, MarketingSourceSnapshot current) {
        return current == null || !current.getId().equals(content.getMarketingSourceSnapshotId())
            || !current.getSnapshotHash().equals(content.getSourceSnapshotHash());
    }
    private void validateRequest(CreateRequest request) {
        if (!REQUEST_CONTRACT.equals(request.contract())) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
    private String browserArtifactUrl(String artifactRef) {
        try { return objectStorage.createPresignedGet(artifactRef).toString(); }
        catch (UnsupportedOperationException ignored) { return artifactRef; }
    }
    private MarketingContent find(Long projectId, String id) {
        return contents.findByIdAndProjectIdAndDeletedAtIsNull(id, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MARKETING_CONTENT_NOT_FOUND));
    }
    private MarketingContent findLocked(Long projectId, String id) {
        return contents.findLocked(id, projectId).orElseThrow(() -> new BusinessException(ErrorCode.MARKETING_CONTENT_NOT_FOUND));
    }
    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
    private void publish(Long projectId, String taskId, String stage, String type, JobEvent.Status status, String code) {
        events.publish(new JobEventPublisher.Command(projectId, taskId, taskId, stage, type, status, type, Map.of(), code));
    }
}
