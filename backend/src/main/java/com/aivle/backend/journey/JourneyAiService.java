package com.aivle.backend.journey;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.document.parsing.DocumentParseRequest;
import com.aivle.backend.document.parsing.DocumentParser;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskResult;
import com.aivle.backend.taskrun.domain.TaskResultValidationState;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.repository.TaskResultRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@Slf4j
public class JourneyAiService {
    private static final Set<String> READINESS = Set.of("UNDER_SPECIFIED", "APPROPRIATE", "OVER_SPECIFIED");
    private static final Set<String> IDEA_RESULT_FIELDS = Set.of("originalSourceSummary", "normalizedDescription",
        "facts", "assumptions", "constraints", "openQuestions", "readiness", "warnings", "evidenceNeeds",
        "originDraft", "fieldMetadata", "clarificationQuestions");
    private static final Set<String> ORIGIN_DRAFT_FIELDS = Set.of("productServiceDescription", "problem", "target",
        "solution", "coreValue", "primaryCategory", "targetRegion", "fixedValues", "confirmedValues", "assumptions",
        "pricingIntent", "revenueModelIntent", "salesChannelIntent", "knownUnitCost", "alternatives",
        "knownCompetitors", "differentiationIntent", "internalConstraints");
    private static final Set<String> ORIGIN_REQUIRED_FIELDS = Set.of("productServiceDescription", "problem", "target",
        "solution", "coreValue", "primaryCategory", "targetRegion", "fixedValues");
    private static final Set<String> LEGAL_STATUSES = Set.of("PASS", "PASS_WITH_CONDITIONS", "REVISION_REQUIRED", "PROHIBITED", "INSUFFICIENT_INFORMATION", "EXPERT_REVIEW_REQUIRED");
    private final ProjectRepository projects;
    private final IdeaSourceRepository sources;
    private final IdeaVersionRepository versions;
    private final IdeaInterpretationRunRepository interpretationRuns;
    private final LegalReviewRunRepository legalRuns;
    private final TaskRunService taskRuns;
    private final TaskResultRepository taskResults;
    private final IdeaInterpretationPersistenceService interpretationPersistence;
    private final IdeaOriginService ideaOrigins;
    private final JourneyLegalReviewPersistenceService legalPersistence;
    private final InternalAiExecutionClient ai;
    private final CanonicalInputHasher hasher;
    private final ObjectMapper mapper;
    private final DocumentParser documentParser;

    public JourneyAiService(ProjectRepository projects, IdeaSourceRepository sources, IdeaVersionRepository versions,
                            IdeaInterpretationRunRepository interpretationRuns, LegalReviewRunRepository legalRuns,
                            TaskRunService taskRuns, InternalAiExecutionClient ai, CanonicalInputHasher hasher,
                            ObjectMapper mapper, DocumentParser documentParser, TaskResultRepository taskResults,
                            IdeaInterpretationPersistenceService interpretationPersistence,
                            JourneyLegalReviewPersistenceService legalPersistence, IdeaOriginService ideaOrigins) {
        this.projects = projects; this.sources = sources; this.versions = versions;
        this.interpretationRuns = interpretationRuns; this.legalRuns = legalRuns; this.taskRuns = taskRuns;
        this.ai = ai; this.hasher = hasher; this.mapper = mapper; this.documentParser = documentParser;
        this.taskResults = taskResults; this.interpretationPersistence = interpretationPersistence;
        this.legalPersistence = legalPersistence; this.ideaOrigins = ideaOrigins;
    }

    public IdeaSourceView saveText(Long ownerId, Long projectId, String title, String text) {
        Project project = ownedProject(ownerId, projectId);
        String normalized = requireText(text);
        return sourceView(sources.save(IdeaSource.create(project, IdeaSource.SourceType.TEXT, trim(title, 200), normalized, null)));
    }

    public IdeaSourceView saveFile(Long ownerId, Long projectId, String title, MultipartFile file) {
        Project project = ownedProject(ownerId, projectId);
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.FILE_REQUIRED);
        String name = file.getOriginalFilename() == null ? "idea.txt" : file.getOriginalFilename().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.length() > 500) name = name.substring(name.length() - 500);
        String lower = name.toLowerCase();
        try {
            String text;
            if (lower.endsWith(".txt")) text = new String(file.getBytes(), StandardCharsets.UTF_8);
            else if (lower.endsWith(".docx")) {
                DocumentParseRequest request = new DocumentParseRequest(name, file.getContentType(), file.getSize(), java.util.Map.of());
                if (!documentParser.supports(request)) throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED);
                text = documentParser.parse(file.getInputStream(), request).plainText();
            } else throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED);
            return sourceView(sources.save(IdeaSource.create(project, IdeaSource.SourceType.FILE, trim(title, 200), requireText(text), name)));
        } catch (IOException failure) {
            throw new BusinessException(ErrorCode.DOCUMENT_PARSE_FAILED);
        }
    }

    public IdeaSourceView currentIdea(Long ownerId, Long projectId) {
        ownedProject(ownerId, projectId);
        return sources.findCurrent(projectId).map(this::sourceView).orElse(null);
    }

    public InterpretationView interpret(Long ownerId, Long projectId) {
        Project project = ownedProject(ownerId, projectId);
        IdeaSource source = sources.findCurrent(projectId).orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_FOUND));
        IdeaInterpretationRun successful = interpretationRuns
            .findTopByProjectIdAndSourceIdAndStateAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, source.getId(), IdeaInterpretationRun.State.SUCCEEDED)
            .orElse(null);
        if (successful != null && successful.getResultJson() != null && hasOriginContract(successful.getResultJson())) {
            ensureOriginDraft(successful);
            return interpretationView(successful);
        }
        IdeaInterpretationRun current = interpretationRuns.findCurrent(projectId).orElse(null);
        if (current != null && current.getSource().getId().equals(source.getId())) {
            if (current.getState() == IdeaInterpretationRun.State.SUCCEEDED && current.getResultJson() != null
                && hasOriginContract(current.getResultJson())) {
                ensureOriginDraft(current);
                return interpretationView(current);
            }
            InterpretationView recovered = current.getState() == IdeaInterpretationRun.State.SUCCEEDED ? null
                : recoverAdoptedResult(ownerId, projectId, current);
            if (recovered != null) return recovered;
            if (current.getState() == IdeaInterpretationRun.State.PENDING
                || current.getState() == IdeaInterpretationRun.State.RUNNING) {
                throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
            }
        }
        IdeaInterpretationRun domainRun = interpretationRuns.save(IdeaInterpretationRun.pending(project, source));
        return executeInterpretation(ownerId, project, source, domainRun.getId());
    }

    @org.springframework.transaction.annotation.Transactional
    public InterpretationView currentInterpretation(Long ownerId, Long projectId) {
        ownedProject(ownerId, projectId);
        IdeaSource source = sources.findCurrent(projectId).orElse(null);
        if (source == null) return null;
        IdeaInterpretationRun run = interpretationRuns.findCurrent(projectId)
            .filter(value -> value.getSource().getId().equals(source.getId())).orElse(null);
        if (run == null) return null;
        TaskRun task = run.getTaskRun();
        if (run.getState() != IdeaInterpretationRun.State.SUCCEEDED && task != null) {
            if (task.getState() == TaskRunState.SUCCEEDED) {
                InterpretationView recovered = recoverAdoptedResult(ownerId, projectId, run);
                if (recovered != null) return recovered;
            } else if (task.getState() == TaskRunState.QUEUED || task.getState() == TaskRunState.READY || task.getState() == TaskRunState.RUNNING) {
                run.retrying(); interpretationRuns.save(run);
            } else if (task.getState() == TaskRunState.FAILED || task.getState() == TaskRunState.TIMED_OUT || task.getState() == TaskRunState.CANCELLED) {
                if (run.getState() != IdeaInterpretationRun.State.FAILED) { run.fail(task.getLastErrorCode()); interpretationRuns.save(run); }
            }
        }
        return interpretationView(run);
    }

    public boolean executeNextInterpretationRetry() {
        IdeaInterpretationRun domainRun = interpretationRuns
            .findTopByTaskRunStateAndTaskRunLastRetryIdempotencyKeyIsNotNullAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                TaskRunState.QUEUED)
            .orElse(null);
        if (domainRun == null) return false;
        TaskRun taskRun = taskRuns.getOwnedForWorker(domainRun.getTaskRun().getId());
        try {
            JsonNode result = execute(taskRun, this::validateIdea);
            interpretationPersistence.complete(domainRun.getId(), parsedIdeaResult(result));
        } catch (ExecutionFailure failure) {
            interpretationPersistence.fail(domainRun.getId(), journeyFailureCode(failure));
            log.warn("Idea interpretation retry failed projectId={} sourceId={} runId={} providerCode={} reason={} retryable={}",
                domainRun.getProject().getId(), domainRun.getSource().getId(), domainRun.getId(),
                failure.code(), failure.reason(), failure.retryable());
        } catch (RuntimeException failure) {
            interpretationPersistence.fail(domainRun.getId(), "AI_RESULT_INVALID");
            log.warn("Idea interpretation retry contract failure projectId={} sourceId={} runId={} code=AI_RESULT_INVALID",
                domainRun.getProject().getId(), domainRun.getSource().getId(), domainRun.getId(), failure);
        }
        return true;
    }

    public IdeaVersionView confirm(Long ownerId, Long projectId, Long versionId) {
        ownedProject(ownerId, projectId);
        Long currentSourceId = sources.findCurrent(projectId).map(IdeaSource::getId)
            .orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_FOUND));
        IdeaVersion version = versions.findCurrent(projectId)
            .filter(value -> versionId.equals(value.getId()) && currentSourceId.equals(value.getSource().getId()))
            .orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_FOUND));
        version.confirm();
        return versionView(versions.save(version));
    }

    public LegalView legalReview(Long ownerId, Long projectId) {
        Project project = ownedProject(ownerId, projectId);
        Long currentSourceId = sources.findCurrent(projectId).map(IdeaSource::getId)
            .orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_FOUND));
        IdeaVersion version = versions.findCurrent(projectId)
            .filter(value -> value.isConfirmed() && currentSourceId.equals(value.getSource().getId()))
            .orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_CONFIRMED));
        LegalReviewRun successful = legalRuns
            .findTopByProjectIdAndIdeaVersionIdAndStateAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                projectId, version.getId(), LegalReviewRun.State.SUCCEEDED)
            .orElse(null);
        if (successful != null && successful.getResultJson() != null) return legalView(successful);

        LegalReviewRun current = legalRuns.findCurrent(projectId).orElse(null);
        Long domainRunId;
        if (current != null && current.getIdeaVersion().getId().equals(version.getId())) {
            LegalView recovered = recoverAdoptedLegal(ownerId, projectId, current);
            if (recovered != null) return recovered;
            if (current.getState() == LegalReviewRun.State.PENDING
                || current.getState() == LegalReviewRun.State.RUNNING) {
                throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
            }
            domainRunId = current.getId();
        } else {
            domainRunId = legalRuns.save(LegalReviewRun.pending(project, version)).getId();
        }
        return executeLegal(ownerId, project, version, domainRunId);
    }

    public LegalView currentLegal(Long ownerId, Long projectId) {
        ownedProject(ownerId, projectId);
        return legalRuns.findCurrent(projectId).map(this::legalView).orElse(null);
    }

    private InterpretationView executeInterpretation(Long ownerId, Project project, IdeaSource source, Long domainRunId) {
        String input = taskInput("idea-source", source.getOriginalText());
        TaskRun run = createTask(ownerId, project, TaskType.IDEA_INTERPRETATION, "IDEA_SOURCE", source.getId().toString(), input);
        interpretationPersistence.markRunning(ownerId, project.getId(), domainRunId, run.getId(), source.getId());
        TaskRun executableRun = taskRuns.getOwnedForWorker(run.getId());
        try {
            JsonNode result = execute(executableRun, this::validateIdea);
            interpretationPersistence.complete(domainRunId, parsedIdeaResult(result));
            return currentInterpretation(ownerId, project.getId());
        } catch (ExecutionFailure failure) {
            interpretationPersistence.fail(domainRunId, journeyFailureCode(failure));
            log.warn("Idea interpretation provider failure projectId={} sourceId={} runId={} providerCode={} reason={} retryable={}",project.getId(),source.getId(),domainRunId,failure.code(),failure.reason(),failure.retryable());
            throw publicAiFailure(failure);
        } catch (RuntimeException invalid) {
            if (invalid instanceof BusinessException business
                && business.getErrorCode() == ErrorCode.RESOURCE_VERSION_CONFLICT) {
                IdeaInterpretationRun latest = interpretationRuns.findCurrent(project.getId()).orElse(null);
                InterpretationView recovered = latest == null ? null
                    : recoverAdoptedResult(ownerId, project.getId(), latest);
                if (recovered != null) return recovered;
                throw business;
            }
            interpretationPersistence.fail(domainRunId, "AI_RESULT_INVALID");
            log.warn("Idea interpretation contract failure projectId={} sourceId={} runId={} code=AI_RESULT_INVALID",project.getId(),source.getId(),domainRunId,invalid);
            throw invalid instanceof BusinessException business ? business : new BusinessException(ErrorCode.AI_RESULT_INVALID);
        }
    }

    private InterpretationView recoverAdoptedResult(Long ownerId, Long projectId, IdeaInterpretationRun run) {
        TaskRun taskRun = run.getTaskRun();
        if (taskRun == null || taskRun.getState() != TaskRunState.SUCCEEDED || taskRun.getFinalResultId() == null) return null;
        TaskResult stored = taskResults.findById(taskRun.getFinalResultId()).orElse(null);
        if (stored == null || stored.getValidationState() != TaskResultValidationState.ADOPTED) return null;
        JsonNode result = parse(stored.getResultJson());
        validateIdea(result);
        interpretationPersistence.complete(run.getId(), parsedIdeaResult(result));
        return currentInterpretation(ownerId, projectId);
    }

    private IdeaInterpretationPersistenceService.ParsedIdeaResult parsedIdeaResult(JsonNode result) {
        return new IdeaInterpretationPersistenceService.ParsedIdeaResult(
            result.toString(), result.get("normalizedDescription").asText(), json(result, "facts"),
            json(result, "assumptions"), json(result, "constraints"), json(result, "openQuestions"),
            IdeaVersion.Readiness.valueOf(result.get("readiness").asText())
        );
    }

    private void ensureOriginDraft(IdeaInterpretationRun run) {
        IdeaVersion version = versions.findTopBySourceIdAndDeletedAtIsNullOrderByVersionNumberDesc(run.getSource().getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_FOUND));
        JsonNode result = parse(run.getResultJson());
        validateIdea(result);
        ideaOrigins.createDraft(run.getProject(), run.getSource(), version, result);
    }

    private boolean hasOriginContract(String resultJson) {
        JsonNode result = parse(resultJson);
        return result != null && result.get("originDraft") != null;
    }

    private LegalView executeLegal(Long ownerId, Project project, IdeaVersion version, Long domainRunId) {
        String input = taskInput("confirmed-idea", legalInput(version));
        TaskRun run = createTask(ownerId, project, TaskType.LEGAL_REVIEW, "IDEA_VERSION", version.getId().toString(), input);
        legalPersistence.markRunning(ownerId, project.getId(), domainRunId, run.getId(), version.getId());
        TaskRun executableRun = taskRuns.getOwnedForWorker(run.getId());
        try {
            JsonNode result = execute(executableRun, this::validateLegal);
            ((ObjectNode) result).put("sourceVerified", false);
            LegalReviewRun.LegalStatus status = LegalReviewRun.LegalStatus.valueOf(result.get("status").asText());
            legalPersistence.complete(domainRunId, status, result.toString());
            return currentLegal(ownerId, project.getId());
        } catch (ExecutionFailure failure) {
            legalPersistence.fail(domainRunId);
            throw publicAiFailure(failure);
        } catch (RuntimeException invalid) {
            if (invalid instanceof BusinessException business
                && business.getErrorCode() == ErrorCode.RESOURCE_VERSION_CONFLICT) {
                LegalReviewRun latest = legalRuns.findCurrent(project.getId()).orElse(null);
                LegalView recovered = latest == null ? null : recoverAdoptedLegal(ownerId, project.getId(), latest);
                if (recovered != null) return recovered;
                throw business;
            }
            legalPersistence.fail(domainRunId);
            throw invalid instanceof BusinessException business ? business : new BusinessException(ErrorCode.AI_RESULT_INVALID);
        }
    }

    private LegalView recoverAdoptedLegal(Long ownerId, Long projectId, LegalReviewRun run) {
        if (run.getState() == LegalReviewRun.State.SUCCEEDED && run.getResultJson() != null) return legalView(run);
        TaskRun taskRun = run.getTaskRun();
        if (taskRun == null || taskRun.getState() != TaskRunState.SUCCEEDED || taskRun.getFinalResultId() == null) return null;
        TaskResult stored = taskResults.findById(taskRun.getFinalResultId()).orElse(null);
        if (stored == null || stored.getValidationState() != TaskResultValidationState.ADOPTED) return null;
        JsonNode result = parse(stored.getResultJson());
        validateLegal(result);
        ((ObjectNode) result).put("sourceVerified", false);
        legalPersistence.complete(run.getId(),
            LegalReviewRun.LegalStatus.valueOf(result.get("status").asText()), result.toString());
        return currentLegal(ownerId, projectId);
    }

    private TaskRun createTask(Long ownerId, Project project, TaskType type, String subjectType, String subjectId, String input) {
        String nonce = UUID.randomUUID().toString();
        return taskRuns.create(ownerId, project.getId(), type, subjectType, subjectId, input,
            hasher.hash(type, "1.0", "ko-KR", input), nonce, nonce, 3);
    }
    private String journeyFailureCode(ExecutionFailure failure) { if ("AI_CONFIGURATION_INVALID".equals(failure.reason())) return "AI_CONFIGURATION_INVALID"; return switch (failure.code()) { case "DEADLINE_EXCEEDED" -> "TASK_TIMEOUT"; case "INVALID_REQUEST", "UNSUPPORTED_CONTRACT_VERSION", "UNSUPPORTED_TASK_TYPE", "UNSUPPORTED_TASK_SCHEMA_VERSION", "RESULT_SCHEMA_INVALID" -> "AI_RESULT_INVALID"; default -> "AI_SERVICE_UNAVAILABLE"; }; }

    private JsonNode execute(TaskRun run, java.util.function.Consumer<JsonNode> validator) {
        TaskRunService.Claim claim = taskRuns.claim(run.getId(), "journey-sync", Duration.ofMinutes(2), Duration.ofMinutes(2));
        taskRuns.startExecution(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        try {
            var response = ai.execute(run, claim.taskAttemptId(), LocalDateTime.now().plusMinutes(2));
            try {
                validator.accept(response.result());
            } catch (BusinessException invalid) {
                taskRuns.rejectAndFail(run.getId(), claim.taskAttemptId(), claim.claimToken(), response.result().toString(),
                    response.resultSchemaVersion(), "AI_RESULT_INVALID");
                throw invalid;
            }
            taskRuns.adopt(run.getId(), claim.taskAttemptId(), claim.claimToken(), response.result().toString(),
                response.canonicalInputHash(), response.resultSchemaVersion());
            return response.result();
        } catch (ExecutionFailure failure) {
            taskRuns.fail(run.getId(), claim.taskAttemptId(), claim.claimToken(), failure.code(), failure.reason(), failure.retryable());
            throw failure;
        }
    }

    private BusinessException publicAiFailure(ExecutionFailure failure) {
        if ("AI_CONFIGURATION_INVALID".equals(failure.reason())) return new BusinessException(ErrorCode.AI_CONFIGURATION_INVALID);
        if ("AI_RESULT_INVALID".equals(failure.reason()) || "RESULT_SCHEMA_INVALID".equals(failure.code())) return new BusinessException(ErrorCode.AI_RESULT_INVALID);
        return new BusinessException(ErrorCode.EXTERNAL_AI_SERVICE_UNAVAILABLE);
    }

    private Project ownedProject(Long ownerId, Long projectId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED));
    }

    private String taskInput(String key, String text) {
        String hash = sha256(text);
        ObjectNode content = mapper.createObjectNode(); content.put("contentKey", key); content.put("contentType", "TEXT");
        int totalCharacters = text.codePointCount(0, text.length());
        content.put("language", "ko-KR"); content.put("totalCharacters", totalCharacters); content.put("contentHash", hash);
        var chunks = content.putArray("chunks");
        int charOffset = 0;
        int index = 0;
        while (charOffset < text.length()) {
            int remaining = text.codePointCount(charOffset, text.length());
            int chunkPoints = Math.min(16_000, remaining);
            int nextOffset = text.offsetByCodePoints(charOffset, chunkPoints);
            String chunkText = text.substring(charOffset, nextOffset);
            ObjectNode chunk = mapper.createObjectNode(); chunk.put("index", index++); chunk.put("text", chunkText);
            chunk.put("characterCount", chunkPoints); chunk.put("chunkHash", sha256(chunkText));
            chunks.add(chunk);
            charOffset = nextOffset;
        }
        ObjectNode root = mapper.createObjectNode(); root.putArray("textContents").add(content);
        return root.toString();
    }

    private String legalInput(IdeaVersion version) {
        return "정규화 설명:\n" + version.getNormalizedDescription()
            + "\n\n사실:\n" + version.getFactsJson()
            + "\n\n가정:\n" + version.getAssumptionsJson()
            + "\n\n제약:\n" + version.getConstraintsJson()
            + "\n\n추가 질문:\n" + version.getOpenQuestionsJson();
    }

    private String sha256(String text) {
        try { return "sha256:" + HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private void validateIdea(JsonNode result) {
        if (result == null || !result.isObject() || !Set.copyOf(result.propertyNames()).equals(IDEA_RESULT_FIELDS)) {
            throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
        }
        requiredText(result, "originalSourceSummary"); requiredText(result, "normalizedDescription");
        requiredArray(result, "facts"); requiredArray(result, "assumptions"); requiredArray(result, "constraints");
        requiredArray(result, "openQuestions"); requiredArray(result, "warnings"); requiredArray(result, "evidenceNeeds");
        if (!READINESS.contains(requiredText(result, "readiness"))) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
        JsonNode origin = result.get("originDraft");
        if (origin == null || !origin.isObject() || !Set.copyOf(origin.propertyNames()).equals(ORIGIN_DRAFT_FIELDS)) {
            throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
        }
        for (String field : List.of("problem", "solution", "coreValue", "fixedValues", "assumptions",
                "alternatives", "knownCompetitors", "internalConstraints")) requiredArray(origin, field);
        for (String field : List.of("problem", "solution", "coreValue", "assumptions", "alternatives",
                "knownCompetitors", "internalConstraints")) validateStringArray(origin.get(field));
        for (String field : List.of("productServiceDescription", "primaryCategory", "targetRegion", "pricingIntent",
                "revenueModelIntent", "salesChannelIntent", "knownUnitCost", "differentiationIntent")) validateNullableText(origin.get(field));
        for (JsonNode fixed : origin.get("fixedValues")) {
            if (!fixed.isObject() || !Set.copyOf(fixed.propertyNames()).equals(Set.of("field", "value"))) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
            requiredText(fixed, "field"); requiredText(fixed, "value");
        }
        if (origin.get("confirmedValues") == null || !origin.get("confirmedValues").isObject()) {
            throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
        }
        JsonNode target = origin.get("target");
        if (target != null && !target.isNull()
            && (!target.isObject() || !Set.copyOf(target.propertyNames()).equals(Set.of("customerTypes", "segment", "situation", "needs")))) {
            throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
        }
        if (target != null && !target.isNull()) {
            requiredArray(target, "customerTypes"); requiredArray(target, "needs");
            validateStringArray(target.get("customerTypes")); validateStringArray(target.get("needs"));
            validateNullableText(target.get("segment")); validateNullableText(target.get("situation"));
        }
        requiredArray(result, "fieldMetadata"); requiredArray(result, "clarificationQuestions");
        for (JsonNode metadata : result.get("fieldMetadata")) {
            if (!metadata.isObject() || !Set.copyOf(metadata.propertyNames()).equals(Set.of(
                    "key", "sourceType", "requiredForStages", "status", "locked", "fallbackPolicy"))) {
                throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
            }
            requiredText(metadata, "key");
            if (!Set.of("USER_CONFIRMED", "AI_PROPOSED").contains(requiredText(metadata, "sourceType"))) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
            requiredArray(metadata, "requiredForStages");
            for (JsonNode stage : metadata.get("requiredForStages")) if (!stage.isTextual()
                || !Set.of("IDEA_ORIGIN", "LEGAL_PRECHECK", "CONCEPT_BUILD").contains(stage.asText())) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
            if (!Set.of("MISSING", "AI_PROPOSED", "USER_CONFIRMED").contains(requiredText(metadata, "status"))) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
            if (!Set.of("NO_FALLBACK", "AI_MAY_PROPOSE", "BLOCK_STAGE").contains(requiredText(metadata, "fallbackPolicy"))) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
            if (metadata.get("locked") == null || !metadata.get("locked").isBoolean()) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
        }
        Set<String> questionTargets = new java.util.HashSet<>();
        for (JsonNode question : result.get("clarificationQuestions")) {
            if (!question.isObject() || !Set.copyOf(question.propertyNames()).equals(Set.of(
                    "targetField", "requirement", "question", "reason"))) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
            questionTargets.add(requiredText(question, "targetField"));
            if (!Set.of("REQUIRED_FOR_IDEA_ORIGIN", "REQUIRED_FOR_LEGAL_PRECHECK").contains(requiredText(question, "requirement"))) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
            requiredText(question, "question"); requiredText(question, "reason");
        }
        for (String field : ORIGIN_REQUIRED_FIELDS) {
            JsonNode value = origin.get(field);
            boolean missing = value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())
                || (value.isArray() && value.isEmpty());
            if (missing && !questionTargets.contains(field)) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
        }
    }
    private void validateLegal(JsonNode result) {
        if (!LEGAL_STATUSES.contains(requiredText(result, "status"))) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
        requiredText(result, "summary"); requiredText(result, "disclaimer"); requiredArray(result, "issues");
        requiredArray(result, "conditions"); requiredArray(result, "prohibitedElements"); requiredArray(result, "researchNeeds");
        if (result.get("sourceVerified") == null || !result.get("sourceVerified").isBoolean()
            || result.get("sourceVerified").asBoolean()) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
    }
    private String requiredText(JsonNode value, String field) {
        JsonNode node = value == null ? null : value.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
        return node.asText();
    }
    private void requiredArray(JsonNode value, String field) {
        if (value == null || value.get(field) == null || !value.get(field).isArray()) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
    }
    private void validateNullableText(JsonNode value) {
        if (value != null && !value.isNull() && !value.isTextual()) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
    }
    private void validateStringArray(JsonNode values) {
        if (values == null || !values.isArray()) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
        for (JsonNode value : values) if (!value.isTextual() || value.asText().isBlank()) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
    }
    private String json(JsonNode value, String field) { return value.get(field).toString(); }
    private JsonNode parse(String value) { return value == null ? null : mapper.readTree(value); }
    private String requireText(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank() || text.length() > 200_000) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return text;
    }
    private String trim(String value, int max) { if (value == null || value.isBlank()) return null; String text = value.trim(); return text.substring(0, Math.min(max, text.length())); }

    private IdeaSourceView sourceView(IdeaSource source) { return new IdeaSourceView(source.getId(), source.getTitle(), source.getSourceType().name(), source.getOriginalText(), source.getOriginalFileReference(), source.getCreatedAt()); }
    private IdeaVersionView versionView(IdeaVersion version) { return new IdeaVersionView(version.getId(), version.getVersionNumber(), version.getNormalizedDescription(), parse(version.getFactsJson()), parse(version.getAssumptionsJson()), parse(version.getConstraintsJson()), parse(version.getOpenQuestionsJson()), version.getReadiness().name(), version.isConfirmed(), version.getCreatedAt()); }
    private InterpretationView interpretationView(IdeaInterpretationRun run) {
        IdeaVersion version = run.getState() == IdeaInterpretationRun.State.SUCCEEDED
            ? versions.findCurrent(run.getProject().getId()).filter(value -> value.getSource().getId().equals(run.getSource().getId())).orElse(null)
            : null;
        return interpretationView(run, version);
    }
    private InterpretationView interpretationView(IdeaInterpretationRun run, IdeaVersion version) { return new InterpretationView(run.getId(), run.getSource().getId(), run.getState().name(), run.getTaskRun() == null ? null : run.getTaskRun().getId(), run.getTaskRun() != null && run.getTaskRun().isRetryable(), parse(run.getResultJson()), run.getError(), version == null ? null : versionView(version), run.getCreatedAt(), run.getCompletedAt()); }
    private LegalView legalView(LegalReviewRun run) { return new LegalView(run.getId(), run.getState().name(), run.getTaskRun() == null ? null : run.getTaskRun().getId(), run.getLegalStatus() == null ? null : run.getLegalStatus().name(), parse(run.getResultJson()), false, run.getIdeaVersion().getId(), run.getCreatedAt(), run.getCompletedAt()); }

    public record IdeaSourceView(Long id, String title, String sourceType, String originalText, String originalFileReference, LocalDateTime createdAt) { }
    public record IdeaVersionView(Long id, int versionNumber, String normalizedDescription, JsonNode facts, JsonNode assumptions, JsonNode constraints, JsonNode openQuestions, String readiness, boolean confirmed, LocalDateTime createdAt) { }
    public record InterpretationView(Long id, Long ideaSourceId, String state, String taskRunId, boolean retryable, JsonNode result, String error, IdeaVersionView ideaVersion, LocalDateTime createdAt, LocalDateTime completedAt) { }
    public record LegalView(Long id, String state, String taskRunId, String legalStatus, JsonNode result, boolean sourceVerified, Long ideaVersionId, LocalDateTime createdAt, LocalDateTime completedAt) { }
}
