package com.aivle.backend.pipeline.marketing.application;

import static com.aivle.backend.pipeline.marketing.api.MarketingApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.file.object.ObjectStoragePort;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver;
import com.aivle.backend.pipeline.currentconcept.CurrentConceptSourceResolver.Source;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class MarketingContentService {
    public static final String REQUEST_CONTRACT = "marketing-content-request-v1";
    private static final String INPUT_SCHEMA_VERSION = "1.0";
    private static final String DESIGN_VERSION = "marketing-draft-v1";
    private static final int MAX_ATTEMPTS = 3;

    private final ProjectRepository projects;
    private final ProjectEvidenceArtifactService evidenceArtifacts;
    private final ObjectStoragePort objectStorage;
    private final CurrentConceptSourceResolver currentConcepts;
    private final MarketingSourceSnapshotService sourceSnapshots;
    private final MarketingSourceSnapshotRepository sourceRepository;
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
    public ContentView create(Long ownerId, Long projectId, CreateRequest request,
            String idempotencyKey, String correlationId) {
        requireOwned(ownerId, projectId);
        validateRequest(request);
        validateReference(ownerId, projectId, request.referenceArtifactId());
        Source authority = requireAuthority(projectId);
        MarketingSourceSnapshot source = sourceSnapshots.requireCurrent(projectId);
        requireBound(source, authority);
        if (!source.getId().equals(request.marketingSourceSnapshotId())) {
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        }
        return enqueueNew(ownerId, projectId, source, mapper.writeValueAsString(request),
            request.contentType(), request.channel(), 1, null, "START", idempotencyKey, correlationId);
    }

    @Transactional
    public ContentView retry(Long ownerId, Long projectId, String id,
            String idempotencyKey, String correlationId) {
        requireOwned(ownerId, projectId);
        MarketingContent previous = findLocked(projectId, id);
        if (previous.getStatus() != MarketingContentStatus.FAILED || previous.getAttempt() >= MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED);
        }
        Source authority = requireAuthority(projectId);
        MarketingSourceSnapshot source = sourceRepository.findById(previous.getMarketingSourceSnapshotId())
            .orElseThrow(() -> new BusinessException(ErrorCode.MODULE_INPUT_STALE));
        if (!bound(previous, source, authority)) {
            previous.markStale();
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE,
                "사업안이 변경되어 이전 마케팅 초안을 재시도할 수 없습니다.");
        }
        CreateRequest request = mapper.readValue(previous.getRequestJson(), CreateRequest.class);
        validateReference(ownerId, projectId, request.referenceArtifactId());
        return enqueueNew(ownerId, projectId, source, previous.getRequestJson(),
            previous.getContentType(), previous.getChannel(), previous.getAttempt() + 1,
            previous.getId(), "RETRY", idempotencyKey, correlationId);
    }

    @Transactional
    public ContentView regenerate(Long ownerId, Long projectId, String id,
            String idempotencyKey, String correlationId) {
        requireOwned(ownerId, projectId);
        MarketingContent previous = findLocked(projectId, id);
        if (!Set.of(MarketingContentStatus.COMPLETED, MarketingContentStatus.FINALIZED,
                MarketingContentStatus.STALE).contains(previous.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "준비된 초안 또는 이전 컨셉의 초안에서만 다른 초안을 만들 수 있습니다.");
        }
        Source authority = requireAuthority(projectId);
        MarketingSourceSnapshot source = sourceSnapshots.requireCurrent(projectId);
        requireBound(source, authority);
        ObjectNode requestJson = (ObjectNode) mapper.readTree(previous.getRequestJson());
        requestJson.put("marketingSourceSnapshotId", source.getId());
        CreateRequest request = mapper.treeToValue(requestJson, CreateRequest.class);
        validateReference(ownerId, projectId, request.referenceArtifactId());
        return enqueueNew(ownerId, projectId, source, mapper.writeValueAsString(requestJson),
            request.contentType(), request.channel(), 1, previous.getId(), "REGENERATE",
            idempotencyKey, correlationId);
    }

    @Transactional
    public ContentListView list(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        Source authority = currentConcepts.currentOrNull(projectId);
        return new ContentListView(contents.findAllByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)
            .stream().map(content -> summaryAndMark(content, authority)).toList());
    }

    @Transactional
    public ContentView current(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId);
        MarketingContent content = contents.findFirstByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)
            .orElse(null);
        return content == null ? null : view(content, currentConcepts.currentOrNull(projectId));
    }

    @Transactional
    public ContentView get(Long ownerId, Long projectId, String id) {
        requireOwned(ownerId, projectId);
        return view(find(projectId, id), currentConcepts.currentOrNull(projectId));
    }

    @Transactional
    public ContentView edit(Long ownerId, Long projectId, String id, EditRequest request) {
        requireOwned(ownerId, projectId);
        MarketingContent content = findLocked(projectId, id);
        Source authority = currentConcepts.currentOrNull(projectId);
        if (markIfStale(content, authority)) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        if (!Set.of(MarketingRevisionType.TONE_EDITED, MarketingRevisionType.SHORTENED,
                MarketingRevisionType.LEGAL_NOTICE_APPLIED, MarketingRevisionType.USER_EDITED)
                .contains(request.revisionType())) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        try {
            results.validate(request.result(), content.getContentType());
            legalGuard.validate(content.getSourceSnapshotJson(), request.result());
            int number = content.addUserRevision();
            revisions.save(MarketingContentRevision.create(id, number, request.revisionType(),
                MarketingRevisionOrigin.USER, mapper.writeValueAsString(request.result()), ownerId));
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, invalid.getMessage());
        }
        return view(content, authority);
    }

    @Transactional
    public ContentView finalizeContent(Long ownerId, Long projectId, String id) {
        requireOwned(ownerId, projectId);
        MarketingContent content = findLocked(projectId, id);
        Source authority = requireAuthority(projectId);
        if (markIfStale(content, authority)) throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        MarketingContentRevision latest = revisions.findFirstByContentIdAndDeletedAtIsNullOrderByRevisionNumberDesc(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        try {
            JsonNode result = mapper.readTree(latest.getResultJson());
            legalGuard.validate(content.getSourceSnapshotJson(), result);
            int number = content.finalizeContent(Instant.now());
            revisions.save(MarketingContentRevision.create(id, number, MarketingRevisionType.FINALIZED,
                MarketingRevisionOrigin.SYSTEM, latest.getResultJson(), ownerId));
        } catch (IllegalStateException invalid) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, invalid.getMessage());
        }
        return view(content, authority);
    }

    private ContentView enqueueNew(Long ownerId, Long projectId, MarketingSourceSnapshot source,
            String requestJson, MarketingContentType type, String channel, int attempt,
            String previousContentId, String operation, String key, String correlation) {
        if (key == null || key.isBlank()) throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        ObjectNode input = mapper.createObjectNode();
        input.set("source", mapper.readTree(source.getSnapshotJson()));
        input.set("request", mapper.readTree(requestJson));
        ObjectNode generation = input.putObject("generation");
        generation.put("operation", operation);
        generation.put("attempt", attempt);
        generation.put("designVersion", DESIGN_VERSION);
        String json = mapper.writeValueAsString(input);
        String hash = inputHasher.hash(TaskType.MARKETING_CONTENT_GENERATION,
            INPUT_SCHEMA_VERSION, "ko-KR", json);
        var created = taskRuns.createWithDisposition(ownerId, projectId,
            TaskType.MARKETING_CONTENT_GENERATION, "MARKETING_EXECUTION", String.valueOf(projectId),
            json, hash, key, correlation == null || correlation.isBlank()
                ? UUID.randomUUID().toString() : correlation, 1);
        if (!created.createdNew()) {
            return view(contents.findByTaskRunIdAndDeletedAtIsNull(created.taskRun().getId())
                .orElseThrow(() -> new IllegalStateException("Marketing TaskRun replay lineage missing")),
                currentConcepts.currentOrNull(projectId));
        }
        String title = mapper.readTree(source.getSnapshotJson()).path("conceptName").asText("Marketing")
            + " - " + type.name();
        MarketingContent content = MarketingContent.queued(UUID.randomUUID().toString(), projectId,
            source.getId(), source.getSnapshotHash(), source.getSnapshotJson(), requestJson,
            type, channel, title, ownerId, attempt, previousContentId);
        content.attachTaskRun(created.taskRun().getId());
        contents.save(content);
        publish(projectId, created.taskRun().getId(), "QUEUED", "job.marketing.queued",
            JobEvent.Status.QUEUED, null);
        return view(content, currentConcepts.currentOrNull(projectId));
    }

    private ContentView view(MarketingContent content, Source authority) {
        return new ContentView(summaryAndMark(content, authority), mapper.readTree(content.getSourceSnapshotJson()),
            mapper.readTree(content.getRequestJson()),
            revisions.findAllByContentIdAndDeletedAtIsNullOrderByRevisionNumberAsc(content.getId()).stream()
                .map(revision -> new RevisionView(revision.getId(), revision.getRevisionNumber(),
                    revision.getRevisionType(), revision.getOrigin(), mapper.readTree(revision.getResultJson())))
                .toList(),
            assets.findAllByContentIdAndDeletedAtIsNullOrderByCreatedAtAsc(content.getId()).stream()
                .map(MarketingAsset::getArtifactRef).map(this::browserArtifactUrl).toList());
    }

    private ContentSummary summaryAndMark(MarketingContent content, Source authority) {
        markIfStale(content, authority);
        String status = content.getStatus().name();
        String activeJobId = Set.of("QUEUED", "RUNNING").contains(status) ? content.getTaskRunId() : null;
        return new ContentSummary(content.getId(), content.getMarketingSourceSnapshotId(),
            content.getSourceSnapshotHash(), content.getContentType(), content.getChannel(), content.getTitle(),
            status, content.getCurrentRevisionNumber(), content.getAttempt(),
            content.getStatus() == MarketingContentStatus.FAILED && content.getAttempt() < MAX_ATTEMPTS,
            content.getPreviousContentId(), content.getTaskRunId(), activeJobId,
            content.getMarketingSourceSnapshotId(), content.getUpdatedAt(), content.getFinalizedAt());
    }

    private boolean markIfStale(MarketingContent content, Source authority) {
        MarketingSourceSnapshot source = sourceRepository.findById(content.getMarketingSourceSnapshotId()).orElse(null);
        boolean stale = content.getStatus() == MarketingContentStatus.STALE
            || source == null || authority == null || !bound(content, source, authority);
        if (stale && content.getStatus() != MarketingContentStatus.STALE) content.markStale();
        return stale;
    }

    private boolean bound(MarketingContent content, MarketingSourceSnapshot source, Source authority) {
        return content.getProjectId().equals(authority.seed().getProjectId())
            && content.getMarketingSourceSnapshotId().equals(source.getId())
            && content.getSourceSnapshotHash().equals(source.getSnapshotHash())
            && source.getSourceSelectionRevision() != null
            && source.getSourceBmPlanRevision() != null
            && source.getSourceMarketSeedSnapshotId().equals(authority.seed().getId())
            && source.getPortfolioSelectionId().equals(authority.selection().getId())
            && source.getSourceSelectionRevision() == authority.selection().getHypothesisRevision()
            && source.getSourceBmPlanRevision() == authority.bm().revision();
    }

    private void requireBound(MarketingSourceSnapshot source, Source authority) {
        if (source.getSourceSelectionRevision() == null || source.getSourceBmPlanRevision() == null
                || !source.getSourceMarketSeedSnapshotId().equals(authority.seed().getId())
                || !source.getPortfolioSelectionId().equals(authority.selection().getId())
                || source.getSourceSelectionRevision() != authority.selection().getHypothesisRevision()
                || source.getSourceBmPlanRevision() != authority.bm().revision()) {
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        }
    }

    private Source requireAuthority(Long projectId) {
        return currentConcepts.require(projectId,
            "현재 확정된 사업안으로 마케팅 초안을 만들 수 없습니다.");
    }

    private void validateRequest(CreateRequest request) {
        if (!REQUEST_CONTRACT.equals(request.contract())) throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    private void validateReference(Long ownerId, Long projectId, String referenceArtifactId) {
        if (referenceArtifactId == null || referenceArtifactId.isBlank()) return;
        var artifact = evidenceArtifacts.requireReferenceable(ownerId, projectId, referenceArtifactId);
        if (!("image/png".equals(artifact.getMediaType()) || "image/jpeg".equals(artifact.getMediaType()))
                || artifact.getSizeBytes() <= 0 || artifact.getSizeBytes() > 20L * 1024 * 1024) {
            throw new BusinessException(ErrorCode.MARKETING_ASSET_INVALID);
        }
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
        return contents.findLocked(id, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MARKETING_CONTENT_NOT_FOUND));
    }

    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private void publish(Long projectId, String taskId, String stage, String type,
            JobEvent.Status status, String code) {
        events.publish(new JobEventPublisher.Command(projectId, taskId, taskId, stage, type,
            status, type, Map.of(), code));
    }
}
