package com.aivle.backend.journey.boundary;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.journey.brief.FieldDecisionStatus;
import com.aivle.backend.journey.brief.OpportunityBriefVersion;
import com.aivle.backend.journey.brief.OpportunityBriefVersionRepository;
import com.aivle.backend.journey.brief.OpportunityFieldValue;
import com.aivle.backend.journey.brief.OpportunityFieldValueRepository;
import com.aivle.backend.journey.foundation.FoundationProjectAccess;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class RegulatoryBoundaryApplicationService {
    static final String REGISTRY_VERSION = "legal-registry-v1";
    static final String PROMPT_VERSION = "regulatory-boundary-v1";
    private final FoundationProjectAccess projects;
    private final OpportunityBriefVersionRepository briefs;
    private final OpportunityFieldValueRepository fields;
    private final RegulatoryBoundaryRunRepository runs;
    private final RegulatoryBoundaryVersionRepository versions;
    private final BoundaryEvidenceRepository evidence;
    private final BoundaryRuleRepository rules;
    private final BoundaryQuestionRepository questions;
    private final TaskRunService tasks;
    private final CanonicalInputHasher hasher;
    private final JobEventPublisher events;
    private final ObjectMapper mapper;

    public RegulatoryBoundaryApplicationService(FoundationProjectAccess projects,
            OpportunityBriefVersionRepository briefs, OpportunityFieldValueRepository fields,
            RegulatoryBoundaryRunRepository runs, RegulatoryBoundaryVersionRepository versions,
            BoundaryEvidenceRepository evidence, BoundaryRuleRepository rules,
            BoundaryQuestionRepository questions, TaskRunService tasks,
            CanonicalInputHasher hasher, JobEventPublisher events, ObjectMapper mapper) {
        this.projects = projects; this.briefs = briefs; this.fields = fields; this.runs = runs;
        this.versions = versions; this.evidence = evidence; this.rules = rules;
        this.questions = questions; this.tasks = tasks; this.hasher = hasher;
        this.events = events; this.mapper = mapper;
    }

    @Transactional
    public StartView start(Long ownerId, Long projectId, Long briefVersionId) {
        Project project = projects.requireOwnedForUpdate(ownerId, projectId);
        OpportunityBriefVersion brief = briefs.findByIdAndProjectIdAndDeletedAtIsNull(briefVersionId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        OpportunityBriefVersion current = currentConfirmed(projectId);
        if (brief.getState() != OpportunityBriefVersion.State.CONFIRMED) {
            return StartView.needsInput(List.of("confirmedOpportunityBrief"),
                "확정된 Opportunity Brief가 필요합니다.", "BRIEF_CONFIRM");
        }
        if (current == null || !current.getId().equals(brief.getId())
                || !current.getSnapshotHash().equals(brief.getSnapshotHash())) {
            return StartView.needsInput(List.of("currentConfirmedOpportunityBrief"),
                "최신 확정 Brief로 다시 시작해 주세요.", "SELECT_CURRENT_CONFIRMED_BRIEF");
        }
        RegulatoryBoundaryRun replay = runs
            .findByProjectIdAndBriefVersionIdAndInputSnapshotHashAndDeletedAtIsNull(
                projectId, brief.getId(), brief.getSnapshotHash()).orElse(null);
        if (replay != null) {
            if (replay.getState() == RegulatoryBoundaryRun.State.FAILED
                    && replay.getTaskRun() != null && replay.getTaskRun().isRetryable()) {
                String retryKey = "boundary-retry-" + replay.getId() + '-' + replay.getTaskRun().getAttemptCount();
                tasks.retry(ownerId, projectId, replay.getTaskRun().getId(), retryKey);
                replay.retryQueued();
                publish(replay, "BOUNDARY", "job.boundary.queued", JobEvent.Status.QUEUED,
                    "job.boundary.queued", Map.of("briefVersionId", brief.getId()), null);
            }
            return startView(replay);
        }

        String input = buildInput(brief);
        String inputHash = hasher.hash(TaskType.REGULATORY_BOUNDARY_GENERATION, "1.0", "ko-KR", input);
        String idempotency = "boundary-" + brief.getId() + '-' + brief.getSnapshotHash().substring(7, 23);
        TaskRun task = tasks.create(ownerId, projectId, TaskType.REGULATORY_BOUNDARY_GENERATION,
            "OPPORTUNITY_BRIEF_VERSION", brief.getId().toString(), input, inputHash,
            idempotency, UUID.randomUUID().toString(), 3);
        RegulatoryBoundaryRun run = runs.save(RegulatoryBoundaryRun.queued(
            project, brief, task, brief.getSnapshotHash()));
        publish(run, "BOUNDARY", "job.boundary.queued", JobEvent.Status.QUEUED,
            "job.boundary.queued", Map.of("briefVersionId", brief.getId()), null);
        return startView(run);
    }

    @Transactional
    public CurrentView current(Long ownerId, Long projectId) {
        projects.requireOwned(ownerId, projectId);
        OpportunityBriefVersion currentBrief = currentConfirmed(projectId);
        staleMismatchedVersions(projectId, currentBrief);
        RegulatoryBoundaryRun latest = runs.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId).orElse(null);
        if (latest == null) return new CurrentView(null, null, false, null);
        RegulatoryBoundaryVersion version = versions.findByRunIdAndDeletedAtIsNull(latest.getId()).orElse(null);
        boolean stale = currentBrief == null || !latest.getBriefVersion().getId().equals(currentBrief.getId())
            || !latest.getInputSnapshotHash().equals(currentBrief.getSnapshotHash());
        return new CurrentView(runView(latest), stale ? null : versionView(version), stale,
            stale && version != null ? version.getId() : null);
    }

    @Transactional
    public VersionView version(Long ownerId, Long projectId, Long versionId) {
        projects.requireOwned(ownerId, projectId);
        OpportunityBriefVersion currentBrief = currentConfirmed(projectId);
        RegulatoryBoundaryVersion version = versions.findByIdAndProjectIdAndDeletedAtIsNull(versionId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (currentBrief == null || !version.getBriefVersion().getId().equals(currentBrief.getId())
                || !version.getBriefSnapshotHash().equals(currentBrief.getSnapshotHash())) {
            version.markStale(LocalDateTime.now());
        }
        return versionView(version);
    }

    @Transactional(readOnly = true)
    public RunView run(Long ownerId, Long projectId, Long runId) {
        projects.requireOwned(ownerId, projectId);
        return runView(runs.findByIdAndProjectIdAndDeletedAtIsNull(runId, projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    private void staleMismatchedVersions(Long projectId, OpportunityBriefVersion current) {
        for (RegulatoryBoundaryVersion version : versions.findByProjectIdAndStatusNotAndDeletedAtIsNull(
                projectId, RegulatoryBoundaryVersion.Status.STALE)) {
            if (current == null || !version.getBriefVersion().getId().equals(current.getId())
                    || !version.getBriefSnapshotHash().equals(current.getSnapshotHash())) {
                version.markStale(LocalDateTime.now());
            }
        }
    }

    private OpportunityBriefVersion currentConfirmed(Long projectId) {
        return briefs.findTopByProjectIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(
            projectId, OpportunityBriefVersion.State.CONFIRMED).orElse(null);
    }

    private String buildInput(OpportunityBriefVersion brief) {
        List<OpportunityFieldValue> values = fields
            .findByBriefVersionIdAndDeletedAtIsNullOrderByFieldKey(brief.getId());
        ObjectNode root = mapper.createObjectNode();
        root.put("mode", "FULL");
        root.set("rerunCategories", mapper.createArrayNode());
        root.put("registryVersion", REGISTRY_VERSION);
        root.put("promptVersion", PROMPT_VERSION);
        root.put("sourceSchemaVersion", "1.0");
        root.put("confirmedBriefVersionId", brief.getId());
        root.put("confirmedBriefHash", brief.getSnapshotHash());
        ArrayNode confirmed = root.putArray("confirmedFacts");
        ArrayNode briefFields = root.putArray("briefFields");
        for (OpportunityFieldValue field : values) {
            ObjectNode value = briefFields.addObject();
            value.put("fieldKey", field.getFieldKey());
            value.set("value", field.getValueJson() == null ? mapper.nullNode() : mapper.readTree(field.getValueJson()));
            value.put("decisionStatus", field.getDecisionStatus().name());
            value.put("sourceType", field.getSourceType().name());
            value.put("userConfirmed", field.isUserConfirmed());
            if (field.isUserConfirmed()) {
                ObjectNode fact = confirmed.addObject();
                fact.put("key", field.getFieldKey());
                fact.set("value", value.get("value"));
                fact.put("source", "USER_CONFIRMED");
                fact.put("decisionStatus", field.getDecisionStatus().name());
            }
        }
        String text = brief.getSnapshotJson();
        ObjectNode content = root.putArray("textContents").addObject();
        content.put("contentKey", "opportunity-brief-v" + brief.getVersionNumber());
        content.put("contentType", "TEXT"); content.put("language", "ko-KR");
        content.put("totalCharacters", text.codePointCount(0, text.length()));
        content.put("contentHash", sha256(text));
        ObjectNode chunk = content.putArray("chunks").addObject();
        chunk.put("index", 0); chunk.put("text", text);
        chunk.put("characterCount", text.codePointCount(0, text.length()));
        chunk.put("chunkHash", sha256(text));
        return mapper.writeValueAsString(root);
    }

    private StartView startView(RegulatoryBoundaryRun run) {
        return new StartView(run.getId(), run.getTaskRun().getId(), run.getState().name(),
            List.of(), null, null);
    }
    private RunView runView(RegulatoryBoundaryRun run) {
        return new RunView(run.getId(), run.getTaskRun() == null ? null : run.getTaskRun().getId(),
            run.getState().name(), run.getTaskRun() != null && run.getTaskRun().isRetryable(),
            run.getErrorCode(), run.getBriefVersion().getId(), run.getInputSnapshotHash());
    }
    private VersionView versionView(RegulatoryBoundaryVersion version) {
        if (version == null) return null;
        List<EvidenceView> evidenceViews = evidence.findByBoundaryVersionIdAndDeletedAtIsNullOrderByEvidenceKey(version.getId())
            .stream().map(this::evidenceView).toList();
        List<RuleView> ruleViews = rules.findByBoundaryVersionIdAndDeletedAtIsNullOrderByRuleKey(version.getId())
            .stream().map(this::ruleView).toList();
        List<QuestionView> questionViews = questions.findByBoundaryVersionIdAndDeletedAtIsNullOrderByQuestionKey(version.getId())
            .stream().map(this::questionView).limit(4).toList();
        JsonNode snapshot = mapper.readTree(version.getSnapshotJson());
        ConceptBuilderInput conceptInput = version.getStatus() == RegulatoryBoundaryVersion.Status.READY
            ? new ConceptBuilderInput(version.getProject().getId(), version.getBriefVersion().getId(),
                version.getBriefSnapshotHash(), version.getId(), version.getSnapshotHash(), "READY",
                ruleViews, List.of(), snapshot.path("userActionOptions"), snapshot.path("sourceWarnings"))
            : null;
        return new VersionView(version.getId(), version.getVersionNumber(), version.getBriefVersion().getId(),
            version.getBriefSnapshotHash(), version.getSnapshotHash(), version.getStatus().name(),
            ruleViews, evidenceViews, questionViews, snapshot.path("conflicts"),
            snapshot.path("userActionOptions"), snapshot.path("sourceWarnings"),
            version.getStatus() == RegulatoryBoundaryVersion.Status.READY, conceptInput);
    }
    private EvidenceView evidenceView(BoundaryEvidence value) {
        return new EvidenceView(value.getEvidenceKey(), value.getSourceType(), value.getLawName(), value.getArticle(),
            value.getTitle(), value.getEffectiveDate(), value.getSourceUrl(), value.getExcerpt(),
            value.getPlainSummary(), value.getWhyRelevant(), value.getSourceStatus(),
            value.getRetrievedAt(), value.getContentHash());
    }
    private RuleView ruleView(BoundaryRule value) {
        return new RuleView(value.getRuleKey(), value.getRuleType().name(), value.getStructureKey(), value.getTitle(),
            value.getDescription(), value.getNormalizedRequirement(), mapper.readTree(value.getEvidenceIdsJson()),
            value.getSeverity(), value.getSourceStatus(), mapper.readTree(value.getAppliesWhenJson()),
            value.getUserFacingReason(), mapper.readTree(value.getAlternativesJson()),
            mapper.readTree(value.getRequiredQualificationsJson()), value.getRequiredPartnerRole(),
            value.getRequiredDisclosure(), mapper.readTree(value.getAffectedBriefFieldsJson()),
            value.isProfessionalReviewRecommended(), mapper.readTree(value.getUserActionOptionsJson()));
    }
    private QuestionView questionView(BoundaryQuestion value) {
        return new QuestionView(value.getQuestionKey(), value.getTargetBriefField(), value.getQuestion(), value.getReason(),
            value.getAnswerType().name(), mapper.readTree(value.getOptionsJson()), value.isRequired(),
            mapper.readTree(value.getRelatedRuleIdsJson()), mapper.readTree(value.getRelatedEvidenceIdsJson()));
    }
    private void publish(RegulatoryBoundaryRun run, String stage, String type, JobEvent.Status status,
            String key, Map<String, ?> params, String code) {
        events.publish(new JobEventPublisher.Command(run.getProject().getId(), run.getTaskRun().getId(),
            run.getTaskRun().getId(), stage, type, status, key, params, code));
    }
    private static String sha256(String value) {
        try { return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record StartView(Long runId, String jobId, String status, List<String> missingPrerequisites,
                            String userMessage, String nextAction) {
        static StartView needsInput(List<String> missing, String message, String action) {
            return new StartView(null, null, "NEEDS_INPUT", missing, message, action);
        }
    }
    public record RunView(Long runId, String jobId, String status, boolean retryable, String errorCode,
                          Long confirmedBriefVersionId, String briefHash) { }
    public record CurrentView(RunView run, VersionView version, boolean stale, Long staleVersionId) { }
    public record VersionView(Long boundaryVersionId, int versionNumber, Long opportunityBriefVersionId,
        String opportunityBriefHash, String regulatoryBoundaryHash, String status,
        List<RuleView> rules, List<EvidenceView> evidence, List<QuestionView> questions,
        JsonNode conflicts, JsonNode userActionOptions, JsonNode sourceWarnings, boolean conceptBuilderAllowed,
        ConceptBuilderInput conceptBuilderInput) { }
    public record ConceptBuilderInput(Long projectId, Long opportunityBriefVersionId, String opportunityBriefHash,
        Long regulatoryBoundaryVersionId, String regulatoryBoundaryHash, String status,
        List<RuleView> rules, List<RuleView> unresolvedFacts, JsonNode userActionOptions, JsonNode sourceWarnings) { }
    public record RuleView(String ruleId, String ruleType, String structureKey, String title, String description,
        String normalizedRequirement, JsonNode evidenceIds, String severity, String sourceStatus,
        JsonNode appliesWhen, String userFacingReason, JsonNode alternatives, JsonNode requiredQualifications,
        String requiredPartnerRole, String requiredDisclosure, JsonNode affectedBriefFields,
        boolean professionalReviewRecommended, JsonNode userActionOptions) { }
    public record EvidenceView(String evidenceId, String sourceType, String lawName, String article, String title,
        String effectiveDate, String officialUrl, String excerpt, String plainSummary, String whyRelevant,
        String sourceStatus, LocalDateTime retrievedAt, String contentHash) { }
    public record QuestionView(String questionId, String fieldKey, String question, String reason,
        String answerType, JsonNode options, boolean required, JsonNode relatedRuleIds, JsonNode relatedEvidenceIds) { }
}
