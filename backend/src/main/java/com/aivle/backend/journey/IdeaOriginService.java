package com.aivle.backend.journey;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class IdeaOriginService {
    private static final List<String> REQUIRED_FIELDS = List.of(
        "productServiceDescription", "problem", "target", "solution", "coreValue",
        "primaryCategory", "targetRegion", "fixedValues"
    );

    private final ProjectRepository projects;
    private final IdeaSourceRepository sources;
    private final IdeaVersionRepository ideaVersions;
    private final IdeaOriginVersionRepository origins;
    private final IdeaClarificationQuestionRepository questions;
    private final LegalPrecheckVersionRepository legalPrechecks;
    private final ObjectMapper mapper;

    public IdeaOriginService(ProjectRepository projects, IdeaSourceRepository sources,
            IdeaVersionRepository ideaVersions, IdeaOriginVersionRepository origins,
            IdeaClarificationQuestionRepository questions, LegalPrecheckVersionRepository legalPrechecks,
            ObjectMapper mapper) {
        this.projects = projects; this.sources = sources; this.ideaVersions = ideaVersions;
        this.origins = origins; this.questions = questions; this.legalPrechecks = legalPrechecks; this.mapper = mapper;
    }

    @Transactional
    public IdeaOriginVersion createDraft(Project project, IdeaSource source, IdeaVersion sourceIdeaVersion,
            JsonNode result) {
        IdeaOriginVersion existing = origins
            .findTopByProjectIdAndSourceIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(
                project.getId(), source.getId(), IdeaOriginVersion.State.DRAFT)
            .orElse(null);
        if (existing != null && existing.getSourceIdeaVersion().getId().equals(sourceIdeaVersion.getId())) return existing;

        JsonNode snapshot = requiredObject(result, "originDraft");
        JsonNode metadata = requiredArray(result, "fieldMetadata");
        JsonNode questionValues = requiredArray(result, "clarificationQuestions");
        ArrayNode missing = mapper.createArrayNode();
        for (JsonNode question : questionValues) missing.add(requiredText(question, "targetField"));
        IdeaOriginVersion draft = origins.save(IdeaOriginVersion.draft(
            project, source, sourceIdeaVersion, nextVersion(project.getId()), snapshot.toString(),
            objectOrEmpty(snapshot.get("confirmedValues")).toString(), arrayOrEmpty(snapshot.get("assumptions")).toString(),
            missing.toString(), metadata.toString()
        ));
        for (JsonNode question : questionValues) {
            questions.save(IdeaClarificationQuestion.create(project, draft,
                trim(requiredText(question, "targetField"), 160),
                IdeaClarificationQuestion.Requirement.valueOf(requiredText(question, "requirement")),
                requiredText(question, "question"), requiredText(question, "reason")));
        }
        return draft;
    }

    @Transactional(readOnly = true)
    public WorkspaceView current(Long ownerId, Long projectId) {
        ownedProject(ownerId, projectId);
        IdeaSource source = sources.findCurrent(projectId).orElse(null);
        if (source == null) return new WorkspaceView(null, null, List.of(), readiness("BLOCKED", "BLOCKED", "BLOCKED"));
        IdeaOriginVersion draft = origins
            .findTopByProjectIdAndSourceIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId, source.getId(), IdeaOriginVersion.State.DRAFT).orElse(null);
        IdeaOriginVersion confirmed = origins
            .findTopByProjectIdAndSourceIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId, source.getId(), IdeaOriginVersion.State.CONFIRMED).orElse(null);
        List<IdeaClarificationQuestion> values = new ArrayList<>();
        if (draft != null) values.addAll(questions.findByOriginDraftVersionIdAndDeletedAtIsNullOrderById(draft.getId()));
        if (confirmed != null) values.addAll(questions.findByOriginDraftVersionIdAndDeletedAtIsNullOrderById(confirmed.getId()));
        boolean missingOrigin = values.stream().anyMatch(value -> value.getRequirement() == IdeaClarificationQuestion.Requirement.REQUIRED_FOR_IDEA_ORIGIN
            && value.getStatus() != IdeaClarificationQuestion.Status.USER_CONFIRMED);
        boolean missingLegal = values.stream().anyMatch(value -> value.getRequirement() == IdeaClarificationQuestion.Requirement.REQUIRED_FOR_LEGAL_PRECHECK
            && value.getStatus() != IdeaClarificationQuestion.Status.USER_CONFIRMED);
        String originReadiness = confirmed != null ? "READY" : draft == null ? "BLOCKED" : "NEEDS_INPUT";
        if (missingOrigin) originReadiness = "NEEDS_INPUT";
        String legalReadiness = confirmed == null ? (draft == null ? "BLOCKED" : "NEEDS_INPUT")
            : missingLegal ? "NEEDS_INPUT" : "READY";
        LegalPrecheckVersion legal = legalPrechecks.findTopByProjectIdAndDeletedAtIsNullOrderByVersionNumberDesc(projectId).orElse(null);
        boolean currentLegal = legal != null && confirmed != null && legal.getIdeaOriginVersion().getId().equals(confirmed.getId());
        String conceptReadiness = !currentLegal ? "BLOCKED" : legal.isConceptBuilderAllowed() && !missingLegal ? "READY" : "NEEDS_INPUT";
        return new WorkspaceView(originView(draft), originView(confirmed),
            values.stream().map(this::questionView).toList(), readiness(originReadiness, legalReadiness, conceptReadiness));
    }

    @Transactional
    public QuestionView answer(Long ownerId, Long projectId, Long questionId, String answer, String answerSource) {
        ownedProject(ownerId, projectId);
        IdeaSource source = sources.findCurrent(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_FOUND));
        IdeaClarificationQuestion question = questions.findLockedById(questionId)
            .filter(value -> value.getProject().getId().equals(projectId)
                && value.getOriginDraftVersion().getSource().getId().equals(source.getId()))
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        IdeaOriginVersion confirmed = origins
            .findTopByProjectIdAndSourceIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId, source.getId(), IdeaOriginVersion.State.CONFIRMED).orElse(null);
        if (confirmed != null && confirmed.getBasedOnOriginVersion() != null
            && confirmed.getBasedOnOriginVersion().getId().equals(question.getOriginDraftVersion().getId())) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }
        question.answer(require(answer, 20_000), require(answerSource, 300));
        return questionView(questions.save(question));
    }

    @Transactional
    public WorkspaceView apply(Long ownerId, Long projectId, Long draftVersionId) {
        ownedProject(ownerId, projectId);
        IdeaSource source = sources.findCurrent(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_FOUND));
        IdeaOriginVersion draft = origins
            .findTopByProjectIdAndSourceIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId, source.getId(), IdeaOriginVersion.State.DRAFT)
            .filter(value -> value.getId().equals(draftVersionId))
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT));
        IdeaOriginVersion currentConfirmed = origins
            .findTopByProjectIdAndSourceIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(
                projectId, source.getId(), IdeaOriginVersion.State.CONFIRMED).orElse(null);
        if (currentConfirmed != null && currentConfirmed.getBasedOnOriginVersion() != null
            && currentConfirmed.getBasedOnOriginVersion().getId().equals(draft.getId())) return current(ownerId, projectId);

        List<IdeaClarificationQuestion> values = questions
            .findByOriginDraftVersionIdAndDeletedAtIsNullOrderById(draft.getId());
        boolean unansweredRequired = values.stream().anyMatch(value ->
            value.getStatus() != IdeaClarificationQuestion.Status.USER_CONFIRMED);
        if (unansweredRequired) throw new BusinessException(ErrorCode.INVALID_REQUEST);

        ObjectNode snapshot = (ObjectNode) mapper.readTree(draft.getSnapshotJson());
        ObjectNode confirmedValues = objectOrEmpty(mapper.readTree(draft.getConfirmedValuesJson()));
        ArrayNode metadata = (ArrayNode) mapper.readTree(draft.getMetadataJson());
        for (IdeaClarificationQuestion question : values) {
            if (question.getStatus() != IdeaClarificationQuestion.Status.USER_CONFIRMED) continue;
            applyAnswer(snapshot, question.getTargetField(), question.getAnswer());
            ObjectNode confirmed = mapper.createObjectNode();
            JsonNode structuredValue = snapshot.get(question.getTargetField());
            if (structuredValue == null) confirmed.put("value", question.getAnswer());
            else confirmed.set("value", structuredValue.deepCopy());
            confirmed.put("source", question.getAnswerSource());
            confirmedValues.set(question.getTargetField(), confirmed);
        }
        confirmMetadata(metadata, snapshot, values);
        List<String> missingRequired = missingRequired(snapshot);
        if (!missingRequired.isEmpty()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        for (String field : REQUIRED_FIELDS) {
            if (confirmedValues.has(field)) continue;
            ObjectNode confirmed = mapper.createObjectNode();
            confirmed.set("value", snapshot.get(field)); confirmed.put("source", "Idea Origin 확정");
            confirmedValues.set(field, confirmed);
        }
        snapshot.set("confirmedValues", confirmedValues);
        ArrayNode missing = mapper.createArrayNode();
        values.stream().filter(value -> value.getStatus() != IdeaClarificationQuestion.Status.USER_CONFIRMED)
            .forEach(value -> missing.add(value.getTargetField()));
        IdeaOriginVersion confirmed = origins.save(IdeaOriginVersion.confirmed(
            draft, nextVersion(projectId), snapshot.toString(), confirmedValues.toString(),
            draft.getAssumptionsJson(), missing.toString(), metadata.toString()
        ));
        draft.getSourceIdeaVersion().confirm();
        ideaVersions.save(draft.getSourceIdeaVersion());
        if (confirmed.getId() == null) throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        return current(ownerId, projectId);
    }

    @Transactional
    public void addLegalQuestions(Project project, IdeaOriginVersion origin, JsonNode requiredInputs) {
        List<IdeaClarificationQuestion> existing = questions
            .findByOriginDraftVersionIdAndDeletedAtIsNullOrderById(origin.getId());
        if (requiredInputs == null || !requiredInputs.isArray()) return;
        int index = 0;
        for (JsonNode item : requiredInputs) {
            String questionText = requiredText(item, "question");
            if (existing.stream().anyMatch(value -> value.getQuestion().equals(questionText))) continue;
            String routeId = item.path("relatedRouteIds").isArray() && !item.path("relatedRouteIds").isEmpty()
                ? item.path("relatedRouteIds").get(0).asText("general") : "general";
            questions.save(IdeaClarificationQuestion.create(project, origin,
                trim("legal." + routeId + "." + index++, 160),
                IdeaClarificationQuestion.Requirement.REQUIRED_FOR_LEGAL_PRECHECK,
                questionText, "법률 적용 범위와 책임 주체를 확정하기 위해 필요합니다."));
        }
    }

    @Transactional
    public WorkspaceView applyLegalAnswers(Long ownerId, Long projectId, Long originVersionId) {
        Project project = ownedProject(ownerId, projectId);
        IdeaOriginVersion origin = origins.findById(originVersionId)
            .filter(value -> value.getProject().getId().equals(projectId)
                && value.getState() == IdeaOriginVersion.State.CONFIRMED && value.getDeletedAt() == null)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT));
        IdeaOriginVersion current = origins.findTopByProjectIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(
            projectId, IdeaOriginVersion.State.CONFIRMED).orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_CONFIRMED));
        if (!current.getId().equals(origin.getId())) throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        List<IdeaClarificationQuestion> values = questions.findByOriginDraftVersionIdAndDeletedAtIsNullOrderById(origin.getId())
            .stream().filter(value -> value.getRequirement() == IdeaClarificationQuestion.Requirement.REQUIRED_FOR_LEGAL_PRECHECK).toList();
        if (values.isEmpty() || values.stream().anyMatch(value -> value.getStatus() != IdeaClarificationQuestion.Status.USER_CONFIRMED))
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        ObjectNode confirmedValues = objectOrEmpty(mapper.readTree(origin.getConfirmedValuesJson()));
        for (IdeaClarificationQuestion question : values) {
            ObjectNode confirmed = mapper.createObjectNode(); confirmed.put("value", question.getAnswer());
            confirmed.put("source", question.getAnswerSource()); confirmedValues.set(question.getTargetField(), confirmed);
        }
        ObjectNode snapshot = (ObjectNode) mapper.readTree(origin.getSnapshotJson()); snapshot.set("confirmedValues", confirmedValues);
        origins.save(IdeaOriginVersion.confirmed(origin, nextVersion(projectId), snapshot.toString(), confirmedValues.toString(),
            origin.getAssumptionsJson(), "[]", origin.getMetadataJson()));
        if (project.getId() == null) throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        return current(ownerId, projectId);
    }

    @Transactional
    public WorkspaceView acceptLegalRevision(Long ownerId, Long projectId, Long originVersionId,
            String targetField, String proposedValue) {
        return acceptLegalRevisions(ownerId, projectId, originVersionId,
            List.of(new LegalRevision(targetField, proposedValue)));
    }

    @Transactional
    public WorkspaceView acceptLegalRevisions(Long ownerId, Long projectId, Long originVersionId,
            List<LegalRevision> revisions) {
        ownedProject(ownerId, projectId);
        if (revisions == null || revisions.isEmpty() || revisions.size() > 50)
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        IdeaOriginVersion origin = origins.findById(originVersionId)
            .filter(value -> value.getProject().getId().equals(projectId)
                && value.getState() == IdeaOriginVersion.State.CONFIRMED && value.getDeletedAt() == null)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT));
        IdeaOriginVersion current = origins.findTopByProjectIdAndStateAndDeletedAtIsNullOrderByVersionNumberDesc(
            projectId, IdeaOriginVersion.State.CONFIRMED).orElseThrow(() -> new BusinessException(ErrorCode.IDEA_NOT_CONFIRMED));
        if (!current.getId().equals(origin.getId())) throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        ObjectNode confirmedValues = objectOrEmpty(mapper.readTree(origin.getConfirmedValuesJson()));
        Set<String> targets = new java.util.HashSet<>();
        for (LegalRevision revision : revisions) {
            String target = require(revision.targetField(), 160);
            if (!targets.add(target)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            ObjectNode confirmed = mapper.createObjectNode();
            confirmed.put("value", require(revision.proposedValue(), 20_000));
            confirmed.put("source", "Legal 수정 계획 사용자 일괄 승인");
            confirmedValues.set(target, confirmed);
        }
        ObjectNode snapshot = (ObjectNode) mapper.readTree(origin.getSnapshotJson()); snapshot.set("confirmedValues", confirmedValues);
        origins.save(IdeaOriginVersion.confirmed(origin, nextVersion(projectId), snapshot.toString(), confirmedValues.toString(),
            origin.getAssumptionsJson(), "[]", origin.getMetadataJson()));
        return current(ownerId, projectId);
    }

    public record LegalRevision(String targetField, String proposedValue) {}

    void applyAnswer(ObjectNode snapshot, String targetField, String answer) {
        switch (targetField) {
            case "productServiceDescription", "primaryCategory", "targetRegion", "pricingIntent",
                 "revenueModelIntent", "salesChannelIntent", "knownUnitCost", "differentiationIntent" ->
                snapshot.put(targetField, answer);
            case "problem", "solution", "coreValue", "alternatives", "knownCompetitors", "internalConstraints" -> {
                ArrayNode value = mapper.createArrayNode(); value.add(answer); snapshot.set(targetField, value);
            }
            case "target" -> {
                ObjectNode target = snapshot.get("target") instanceof ObjectNode existing
                    ? existing.deepCopy() : mapper.createObjectNode();
                ArrayNode customerTypes = target.get("customerTypes") instanceof ArrayNode existingTypes
                    ? existingTypes : target.putArray("customerTypes");
                boolean alreadyPresent = false;
                for (JsonNode value : customerTypes) {
                    if (value.isTextual() && value.asText().equals(answer)) alreadyPresent = true;
                }
                if (!alreadyPresent) customerTypes.add(answer);
                if (!target.has("segment")) target.putNull("segment");
                if (!target.has("situation")) target.putNull("situation");
                if (!(target.get("needs") instanceof ArrayNode)) target.putArray("needs");
                snapshot.set("target", target);
            }
            case "fixedValues" -> {
                ArrayNode fixed = mapper.createArrayNode(); ObjectNode item = mapper.createObjectNode();
                item.put("field", "userConfirmed"); item.put("value", answer); fixed.add(item);
                snapshot.set("fixedValues", fixed);
            }
            default -> { /* Conditional Legal facts stay in confirmedValues without changing the Origin shape. */ }
        }
    }

    private void confirmMetadata(ArrayNode metadata, ObjectNode snapshot, List<IdeaClarificationQuestion> answers) {
        List<String> answeredFields = answers.stream()
            .filter(value -> value.getStatus() == IdeaClarificationQuestion.Status.USER_CONFIRMED)
            .map(IdeaClarificationQuestion::getTargetField).toList();
        for (JsonNode item : metadata) {
            if (!(item instanceof ObjectNode object)) continue;
            String key = object.path("key").asText();
            if (answeredFields.contains(key) || present(snapshot.get(key))) {
                object.put("sourceType", "USER_CONFIRMED"); object.put("status", "USER_CONFIRMED");
                object.put("locked", true);
            }
        }
    }

    private List<String> missingRequired(ObjectNode snapshot) {
        List<String> missing = new ArrayList<>();
        for (String field : REQUIRED_FIELDS) if (!present(snapshot.get(field))) missing.add(field);
        return missing;
    }

    private boolean present(JsonNode value) {
        return value != null && !value.isNull()
            && (!value.isTextual() || !value.asText().isBlank())
            && (!value.isArray() || value.size() > 0)
            && (!value.isObject() || value.size() > 0);
    }

    private int nextVersion(Long projectId) {
        return Math.toIntExact(origins.countByProjectIdAndDeletedAtIsNull(projectId) + 1);
    }

    private Project ownedProject(Long ownerId, Long projectId) {
        return projects.findByIdAndOwnerIdAndDeletedAtIsNull(projectId, ownerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private ObjectNode requiredObject(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (!(value instanceof ObjectNode object)) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
        return object;
    }
    private ArrayNode requiredArray(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (!(value instanceof ArrayNode array)) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
        return array;
    }
    private String requiredText(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) throw new BusinessException(ErrorCode.AI_RESULT_INVALID);
        return value.asText();
    }
    private ObjectNode objectOrEmpty(JsonNode value) { return value instanceof ObjectNode object ? object : mapper.createObjectNode(); }
    private ArrayNode arrayOrEmpty(JsonNode value) { return value instanceof ArrayNode array ? array : mapper.createArrayNode(); }
    private String require(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > max) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return normalized;
    }
    private String trim(String value, int max) { return value.substring(0, Math.min(max, value.length())); }

    private OriginView originView(IdeaOriginVersion value) {
        return value == null ? null : new OriginView(value.getId(), value.getVersionNumber(), value.getState().name(),
            value.getSourceIdeaVersion().getId(), mapper.readTree(value.getSnapshotJson()),
            mapper.readTree(value.getConfirmedValuesJson()), mapper.readTree(value.getAssumptionsJson()),
            mapper.readTree(value.getMissingFieldsJson()), mapper.readTree(value.getMetadataJson()),
            value.getConfirmedAt(), value.getCreatedAt());
    }
    private QuestionView questionView(IdeaClarificationQuestion value) {
        return new QuestionView(value.getId(), value.getOriginDraftVersion().getId(), value.getTargetField(),
            value.getRequirement().name(), value.getQuestion(), value.getReason(), value.getAnswer(),
            value.getAnswerSource(), value.getStatus().name(), value.getAnsweredAt());
    }
    private ReadinessView readiness(String idea, String legal, String concept) { return new ReadinessView(idea, legal, concept); }

    public record WorkspaceView(OriginView draft, OriginView confirmed, List<QuestionView> questions, ReadinessView readiness) { }
    public record OriginView(Long id, int versionNumber, String state, Long sourceIdeaVersionId, JsonNode snapshot,
        JsonNode confirmedValues, JsonNode assumptions, JsonNode missingFields, JsonNode metadata,
        LocalDateTime confirmedAt, LocalDateTime createdAt) { }
    public record QuestionView(Long id, Long originDraftVersionId, String targetField, String requirement,
        String question, String reason, String answer, String answerSource, String status, LocalDateTime answeredAt) { }
    public record ReadinessView(String ideaOrigin, String legalPrecheck, String conceptBuild) { }
}
