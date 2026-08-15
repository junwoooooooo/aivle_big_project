package com.aivle.backend.pipeline.marketing.visual.application;

import static com.aivle.backend.pipeline.marketing.visual.api.MarketingVisualApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.artifact.application.ProjectEvidenceArtifactService;
import com.aivle.backend.pipeline.marketing.domain.MarketingContent;
import com.aivle.backend.pipeline.marketing.domain.MarketingContentRevision;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRepository;
import com.aivle.backend.pipeline.marketing.repository.MarketingContentRevisionRepository;
import com.aivle.backend.pipeline.marketing.visual.api.MarketingVisualApiModels;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskResult;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import com.aivle.backend.taskrun.repository.TaskRunRepository;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.util.Base64;
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
public class MarketingVisualService {
    public static final String CONTRACT = "marketing-visual-generation-input-v1";
    public static final String SUBJECT_TYPE = "MARKETING_VISUAL";
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final Set<String> MOODS = Set.of("신뢰감 있는", "밝고 친근한", "감성적인", "전문적인",
        "강렬한", "고급스러운", "미니멀한");
    private static final Set<String> FORMATS = Set.of("가로형 배너", "정사각형 SNS 광고", "세로형 모바일 광고");

    private final ProjectRepository projects;
    private final MarketingContentRepository contents;
    private final MarketingContentRevisionRepository revisions;
    private final ProjectEvidenceArtifactService artifacts;
    private final TaskRunService taskRuns;
    private final TaskRunRepository taskRunRepository;
    private final TaskResultRepository taskResults;
    private final CanonicalInputHasher hasher;
    private final JobEventPublisher events;
    private final ObjectMapper mapper;

    @Transactional
    public VisualRunView create(Long ownerId, Long projectId, CreateRequest request,
            String idempotencyKey, String correlationId) {
        requireOwned(ownerId, projectId);
        validate(request);
        String input = input(ownerId, projectId, request);
        TaskRun run = enqueue(ownerId, projectId, request.marketingContentId(), input,
            idempotencyKey, correlationId);
        publish(projectId, run.getId(), "QUEUED", "job.marketing.visual.queued",
            JobEvent.Status.QUEUED, null);
        return view(run);
    }

    @Transactional(readOnly = true)
    public VisualRunView get(Long ownerId, Long projectId, String taskRunId) {
        return view(requireVisual(taskRuns.getOwned(ownerId, projectId, taskRunId)));
    }

    @Transactional(readOnly = true)
    public VisualRunView current(Long ownerId, Long projectId, String contentId) {
        requireOwned(ownerId, projectId);
        TaskRun run = taskRunRepository
            .findFirstByProjectIdAndTaskTypeAndSubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, TaskType.MARKETING_VISUAL_GENERATION, SUBJECT_TYPE, contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return view(run);
    }

    @Transactional
    public VisualRunView retry(Long ownerId, Long projectId, String taskRunId,
            String idempotencyKey, String correlationId) {
        TaskRun previous = requireVisual(taskRuns.getOwned(ownerId, projectId, taskRunId));
        if (!previous.terminal() || previous.getState() == TaskRunState.SUCCEEDED
                || !previous.isRetryable()) {
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED);
        }
        TaskRun run = enqueue(ownerId, projectId, previous.getSubjectId(), previous.getInputSnapshot(),
            idempotencyKey, correlationId);
        publish(projectId, run.getId(), "QUEUED", "job.marketing.visual.queued",
            JobEvent.Status.QUEUED, null);
        return view(run);
    }

    @Transactional
    public VisualRunView cancel(Long ownerId, Long projectId, String taskRunId) {
        requireVisual(taskRuns.getOwned(ownerId, projectId, taskRunId));
        return view(taskRuns.cancel(ownerId, projectId, taskRunId));
    }

    @Transactional(readOnly = true)
    public ObjectNode resolveExecutionInput(TaskRunWorkerContext context) {
        ObjectNode input = (ObjectNode) mapper.readTree(context.inputSnapshot());
        JsonNode source = input.path("sourceImage");
        var resolved = artifacts.resolveOwned(context.ownerId(), context.projectId(),
            source.path("artifactId").asText(), MAX_IMAGE_BYTES);
        if (!resolved.artifact().originalFilename().equals(source.path("originalFilename").asText())
                || !resolved.artifact().mediaType().equals(source.path("mediaType").asText())
                || resolved.artifact().sizeBytes() != source.path("sizeBytes").asLong()) {
            throw new BusinessException(ErrorCode.MARKETING_ASSET_INVALID);
        }
        ObjectNode bytes = mapper.createObjectNode();
        bytes.put("bytesBase64", Base64.getEncoder().encodeToString(resolved.content()));
        input.set("resolvedSourceImage", bytes);
        return input;
    }

    public void publish(Long projectId, String taskId, String stage, String messageKey,
            JobEvent.Status status, String code) {
        events.publish(new JobEventPublisher.Command(projectId, taskId, taskId, stage,
            messageKey, status, messageKey, Map.of(), code));
    }

    private TaskRun enqueue(Long ownerId, Long projectId, String contentId, String input,
            String idempotencyKey, String correlationId) {
        String hash = hasher.hash(TaskType.MARKETING_VISUAL_GENERATION, "1.0", "ko-KR", input);
        return taskRuns.create(ownerId, projectId, TaskType.MARKETING_VISUAL_GENERATION,
            SUBJECT_TYPE, contentId, input, hash, idempotencyKey,
            correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId, 2);
    }

    private String input(Long ownerId, Long projectId, CreateRequest request) {
        MarketingContent content = contents.findByIdAndProjectIdAndDeletedAtIsNull(
            request.marketingContentId(), projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MARKETING_CONTENT_NOT_FOUND));
        MarketingContentRevision revision = revisions
            .findAllByContentIdAndDeletedAtIsNullOrderByRevisionNumberAsc(content.getId()).stream()
            .filter(value -> value.getId().equals(request.marketingRevisionId())).findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST));
        var artifact = artifacts.requireReferenceable(ownerId, projectId, request.sourceImageArtifactId());
        if (!Set.of("image/png", "image/jpeg", "image/webp").contains(artifact.getMediaType())
                || artifact.getSizeBytes() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.MARKETING_ASSET_INVALID);
        }
        ObjectNode root = mapper.createObjectNode();
        root.put("contract", CONTRACT);
        root.put("marketingContentId", content.getId());
        root.put("marketingRevisionId", revision.getId());
        root.set("source", mapper.readTree(content.getSourceSnapshotJson()));
        root.set("content", mapper.readTree(revision.getResultJson()));
        ObjectNode sourceImage = mapper.createObjectNode();
        sourceImage.put("artifactId", artifact.getId());
        sourceImage.put("originalFilename", artifact.getOriginalFilename());
        sourceImage.put("mediaType", artifact.getMediaType());
        sourceImage.put("sizeBytes", artifact.getSizeBytes());
        root.set("sourceImage", sourceImage);
        ObjectNode visual = mapper.createObjectNode();
        visual.put("promotionName", request.promotionName().strip());
        visual.put("mainBanner", request.mainBanner().strip());
        visual.put("supportingCopy", request.supportingCopy().strip());
        visual.put("mood", request.mood());
        visual.put("bannerFormat", request.bannerFormat());
        visual.set("emphasisKeywords", mapper.valueToTree(request.emphasisKeywords().stream()
            .map(String::strip).filter(value -> !value.isBlank()).distinct().toList()));
        root.set("visual", visual);
        return mapper.writeValueAsString(root);
    }

    private void validate(CreateRequest request) {
        if (!CONTRACT.equals(request.contract()) || !MOODS.contains(request.mood())
                || !FORMATS.contains(request.bannerFormat())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private VisualRunView view(TaskRun run) {
        JsonNode input = mapper.readTree(run.getInputSnapshot());
        JsonNode result = null;
        if (run.getFinalResultId() != null) {
            result = taskResults.findById(run.getFinalResultId()).map(TaskResult::getResultJson)
                .map(mapper::readTree).orElse(null);
        }
        boolean active = Set.of(TaskRunState.QUEUED, TaskRunState.READY, TaskRunState.RUNNING).contains(run.getState());
        return new MarketingVisualApiModels.VisualRunView(run.getId(), run.getState().name(),
            run.isRetryable(), run.getLastErrorCode(), active ? run.getId() : null,
            input.path("marketingContentId").asText(), input.path("marketingRevisionId").asText(),
            input.path("sourceImage").path("artifactId").asText(), result,
            run.getCreatedAt(), run.getFinishedAt());
    }

    private TaskRun requireVisual(TaskRun run) {
        if (run.getTaskType() != TaskType.MARKETING_VISUAL_GENERATION
                || !SUBJECT_TYPE.equals(run.getSubjectType())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return run;
    }

    private void requireOwned(Long ownerId, Long projectId) {
        projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
