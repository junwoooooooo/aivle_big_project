package com.aivle.backend.pipeline.concept.application;

import com.aivle.backend.pipeline.concept.domain.*;
import com.aivle.backend.pipeline.concept.repository.*;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.legal.application.CanonicalLegalContextAssembler;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefRepository;
import com.aivle.backend.pipeline.legal.domain.*;
import com.aivle.backend.pipeline.legal.repository.*;
import java.util.*;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ConceptFactoryExecutionService {
    private final ConceptFactoryRunRepository runs;
    private final ConceptSlotRepository slots;
    private final ConceptAttemptRepository attempts;
    private final ConceptRepository concepts;
    private final IdeaBriefFieldRepository ideaFields;
    private final IdeaBriefRepository ideaBriefs;
    private final LegalContextPackRepository contexts;
    private final LegalEvidenceRepository evidence;
    private final ConceptLegalAssessmentRepository assessments;
    private final ConceptLegalEvidenceLinkRepository evidenceLinks;
    private final ConceptRejectionSummaryRepository rejections;
    private final ObjectMapper mapper;
    private final CanonicalLegalContextAssembler legalContextAssembler;
    private final ConceptLegalFactPatternMapper legalFactPatterns;
    @Value("${app.legal.registry-version:legal-registry-v1}")
    private String registryVersion;

    @Transactional
    public Work prepare(String runId, Long projectId) {
        ConceptFactoryRun run = runs.findById(runId).filter(v -> v.getProject().getId().equals(projectId)).orElseThrow();
        if (run.getStatus() == ConceptFactoryRunStatus.QUEUED) run.transitionTo(ConceptFactoryRunStatus.GENERATING);
        if (run.getStatus() == ConceptFactoryRunStatus.GENERATING) run.transitionTo(ConceptFactoryRunStatus.VALIDATING);
        LegalContextPack pack = contexts.findByProjectIdAndSourceSnapshotIdAndDeletedAtIsNull(projectId, run.getSourceIdeaBriefSnapshotId())
            .orElseGet(() -> createContext(run));
        List<Map<String, String>> fields = new ArrayList<>(ideaFields.findAllByBriefIdOrderById(run.getSourceIdeaBriefSnapshotId()).stream()
            .filter(value -> value.getFieldValue() != null && !value.getFieldValue().isBlank())
            .map(value -> Map.of(
                "fieldKey", value.getFieldKey(),
                "value", value.getFieldValue(),
                "source", switch (value.getProvenance()) {
                    case USER_INPUT -> "USER_INPUT";
                    case USER_CONFIRMED -> "USER_CONFIRMED";
                    default -> "AI_DERIVED";
                },
                "authority", switch (value.getDecisionState()) {
                    case LOCKED -> "LOCKED";
                    case REVIEWABLE -> "REVIEWABLE";
                    default -> "OPEN";
                }
            )).toList());
        JsonNode interpretation = mapper.readTree(ideaBriefs.findById(run.getSourceIdeaBriefSnapshotId())
            .orElseThrow().getInterpretationJson());
        for (String key : List.of("interpretedProblem", "interpretedTargetUsers", "usageContext", "industryCategory",
                "researchScope", "conciseIdeaDefinition", "targetRegionInterpretation",
                "relevantKnownCompetitorContext")) {
            String value = interpretation.path(key).asText();
            if (!value.isBlank()) fields.add(Map.of("fieldKey", key, "value", value,
                "source", "AI_DERIVED", "authority", "REVIEWABLE"));
        }
        ConceptGenerationStrategy strategy = ConceptGenerationStrategyPolicy.decide(fields);
        Map<String, Object> externalFacts = externalFactContext(pack);
        List<SlotWork> slotWork = slots.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotNumber(runId, projectId).stream()
            .filter(value -> value.getStatus() != ConceptSlotStatus.ELIGIBLE)
            .map(value -> {
                ConceptAttempt candidate = attempts.findAllBySlotIdOrderByAttemptNumber(value.getId()).stream()
                    .filter(attempt -> attempt.getPhase() != ConceptAttemptPhase.LEGAL_REVIEW
                        && attempt.getResultJson() != null && attempt.getErrorClassification() == null)
                    .reduce((first, second) -> second).orElse(null);
                return new SlotWork(value.getId(), value.getSlotNumber(), value.getVariationFocus(),
                    value.getLegalRedesignCount(), value.getReplacementRounds(),
                    candidate == null ? null : candidate.getId(),
                    candidate == null ? null : candidate.getResultJson());
            }).toList();
        return new Work(runId, projectId, run.getSourceIdeaBriefSnapshotId(), strategy, fields, externalFacts, slotWork);
    }

    private LegalContextPack createContext(ConceptFactoryRun run) {
        CanonicalLegalContextAssembler.Result assembled = legalContextAssembler.assemble(
            ideaFields.findAllByBriefIdOrderById(run.getSourceIdeaBriefSnapshotId()));
        return contexts.save(LegalContextPack.ready(run.getProject(), run.getSourceIdeaBriefSnapshotId(),
            run.getSourceSnapshotHash(), assembled.contextJson(), assembled.provenanceJson(), registryVersion));
    }

    private Map<String, Object> externalFactContext(LegalContextPack pack) {
        return Map.of("sourceSnapshotHash", pack.getSourceSnapshotHash(),
            "registryVersion", pack.getRegistryVersion(),
            "facts", mapper.readTree(pack.getCanonicalContextJson()));
    }

    public ConceptLegalFactPatternMapper.Result legalFactPattern(JsonNode candidate) {
        return legalFactPatterns.map(candidate);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String beginAttempt(String slotId, ConceptAttemptPhase phase, String taskRunId) {
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        if (slot.getStatus() != ConceptSlotStatus.GENERATING) slot.transitionTo(ConceptSlotStatus.GENERATING);
        return attempts.save(ConceptAttempt.begin(slot, phase, taskRunId)).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String beginRetryAttempt(String slotId, ConceptAttemptPhase phase, String taskRunId) {
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        return attempts.save(ConceptAttempt.retry(slot, phase, taskRunId)).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String beginLegalReviewAttempt(String slotId, String taskRunId) {
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        if (slot.getStatus() == ConceptSlotStatus.REVIEW_RETRY_PENDING) slot.resumeLegalReview();
        return attempts.save(ConceptAttempt.retry(slot, ConceptAttemptPhase.LEGAL_REVIEW, taskRunId)).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCandidateInspection(String runId) {
        runs.findById(runId).orElseThrow().recordCandidateInspection();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordProviderTransientRetry(String runId) {
        runs.findById(runId).orElseThrow().recordProviderTransientRetry();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCompletedRedesign(String slotId, String attemptId) {
        ConceptAttempt attempt = attempts.findById(attemptId).orElseThrow();
        if (attempt.getPhase() == ConceptAttemptPhase.REDESIGN && attempt.getResultJson() != null
                && attempt.getErrorClassification() == null) {
            slots.findById(slotId).orElseThrow().recordCompletedRedesign();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void pauseGenerationForRetry(String slotId) {
        slots.findById(slotId).orElseThrow().fail();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generated(String slotId, String attemptId, JsonNode candidate) {
        attempts.findById(attemptId).orElseThrow().succeed(mapper.writeValueAsString(candidate));
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        slot.transitionTo(ConceptSlotStatus.GENERATED);
        slot.transitionTo(ConceptSlotStatus.VALIDATING_ORIGIN);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CandidateDisposition validateCandidate(String runId, String slotId, String attemptId, JsonNode candidate,
            ConceptGenerationStrategy strategy, int candidateIndex, List<Map<String, String>> fields) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        attempts.findById(attemptId).orElseThrow();
        if (slot.getStatus() == ConceptSlotStatus.QUEUED) {
            slot.transitionTo(ConceptSlotStatus.GENERATING);
            slot.transitionTo(ConceptSlotStatus.GENERATED);
            slot.transitionTo(ConceptSlotStatus.VALIDATING_ORIGIN);
        } else if (slot.getStatus() == ConceptSlotStatus.GENERATED) {
            slot.transitionTo(ConceptSlotStatus.VALIDATING_ORIGIN);
        }
        ConceptCandidateV2Validator.Result origin = ConceptCandidateV2Validator.validate(
            candidate, strategy, candidateIndex, fields);
        if (!origin.accepted()) {
            rejectCandidate(slot, attemptId, candidate, origin.error(), origin.safeCode(),
                "확정한 Market Seed 조건을 보존하지 못한 후보입니다.");
            return origin.error() == ConceptAttemptError.LOCKED_CONSTRAINT_INVALID
                ? CandidateDisposition.LOCKED_INVALID : CandidateDisposition.ORIGIN_INVALID;
        }
        if (slot.getStatus() == ConceptSlotStatus.VALIDATING_ORIGIN) {
            slot.transitionTo(ConceptSlotStatus.VALIDATING_DISTINCTNESS);
        }
        for (Concept existing : concepts.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotSlotNumber(
                runId, run.getProject().getId())) {
            if (ConceptFingerprint.classify(candidate, mapper.readTree(existing.getCandidateJson()), slot.getVariationFocus())
                    == ConceptFingerprint.Classification.DUPLICATE) {
                rejectCandidate(slot, attemptId, candidate, ConceptAttemptError.DUPLICATE_CONCEPT,
                    "DUPLICATE_CONCEPT", "이름이나 표현 외의 실질적 사업 구조가 기존 후보와 같은 후보입니다.");
                return CandidateDisposition.DUPLICATE;
            }
        }
        for (ConceptAttempt previous : attempts.findAllBySlotIdOrderByAttemptNumber(slotId)) {
                if (previous.getId().equals(attemptId) || previous.getPhase() == ConceptAttemptPhase.LEGAL_REVIEW
                    || previous.getResultJson() == null) continue;
                if (ConceptFingerprint.classify(candidate, mapper.readTree(previous.getResultJson()), slot.getVariationFocus())
                        == ConceptFingerprint.Classification.DUPLICATE) {
                    rejectCandidate(slot, attemptId, candidate, ConceptAttemptError.DUPLICATE_CONCEPT,
                        "DUPLICATE_CONCEPT", "이름이나 표현 외의 실질적 사업 구조가 이전 후보와 같은 후보입니다.");
                    return CandidateDisposition.DUPLICATE;
                }
        }
        if (!semanticComparisons(runId, slotId, attemptId, candidate).isEmpty()) {
            return CandidateDisposition.SEMANTIC_REVIEW_REQUIRED;
        }
        if (slot.getStatus() == ConceptSlotStatus.VALIDATING_DISTINCTNESS) {
            slot.transitionTo(ConceptSlotStatus.VALIDATING_LEGAL);
        }
        return CandidateDisposition.ACCEPTED;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> semanticComparisons(String runId, String slotId, String attemptId,
            JsonNode candidate) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        List<JsonNode> previous = new ArrayList<>();
        concepts.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotSlotNumber(runId, run.getProject().getId())
            .forEach(value -> previous.add(mapper.readTree(value.getCandidateJson())));
        for (ConceptAttempt attempt : attempts.findAllBySlotIdOrderByAttemptNumber(slotId)) {
                if (attempt.getId().equals(attemptId) || attempt.getPhase() == ConceptAttemptPhase.LEGAL_REVIEW
                        || attempt.getResultJson() == null) continue;
                previous.add(mapper.readTree(attempt.getResultJson()));
        }
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        ConceptSlot currentSlot = slots.findById(slotId).orElseThrow();
        for (JsonNode existing : previous) {
            if (ConceptFingerprint.classify(candidate, existing, currentSlot.getVariationFocus())
                    != ConceptFingerprint.Classification.AMBIGUOUS) continue;
            String hash = ConceptFingerprint.from(existing).canonicalHash();
            if (seen.add(hash)) result.add(ConceptFingerprint.businessSummary(existing));
        }
        return List.copyOf(result);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void acceptSemanticDistinctness(String slotId) {
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        if (slot.getStatus() == ConceptSlotStatus.VALIDATING_DISTINCTNESS) {
            slot.transitionTo(ConceptSlotStatus.VALIDATING_LEGAL);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejectSemanticDuplicate(String slotId, String attemptId, JsonNode candidate) {
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        rejectCandidate(slot, attemptId, candidate, ConceptAttemptError.DUPLICATE_CONCEPT,
            "DUPLICATE_CONCEPT", "표현과 무관하게 실질적 사업 구조가 기존 후보와 같은 후보입니다.");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> eligibleConceptFingerprints(String runId) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        return concepts.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotSlotNumber(
                runId, run.getProject().getId()).stream()
            .map(value -> ConceptFingerprint.businessSummary(mapper.readTree(value.getCandidateJson()))).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> acceptedFingerprints(String runId) {
        return eligibleConceptFingerprints(runId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> currentSlotSearchHistory(String slotId) {
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        List<Map<String, Object>> values = fingerprintsFromAttempts(List.of(slot));
        return values.stream().skip(Math.max(0, values.size() - 5L)).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> currentSlotPreviousFingerprints(String slotId) {
        return currentSlotSearchHistory(slotId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> softRejectedExamples(String runId, String currentSlotId) {
        LinkedHashMap<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (ConceptRejectionSummary rejection :
                rejections.findAllBySlotRunIdAndDeletedAtIsNullOrderByIdAsc(runId)) {
            if (rejection.getSlot().getId().equals(currentSlotId) || rejection.getAttemptId() == null) continue;
            attempts.findById(rejection.getAttemptId()).filter(attempt ->
                    attempt.getResultJson() != null
                    && attempt.getPhase() != ConceptAttemptPhase.LEGAL_REVIEW
                    && attempt.getErrorClassification() != null).ifPresent(attempt -> {
                JsonNode rejected = mapper.readTree(attempt.getResultJson());
                unique.putIfAbsent(ConceptFingerprint.from(rejected).canonicalHash(),
                    ConceptFingerprint.businessSummary(rejected));
            });
        }
        List<Map<String, Object>> values = List.copyOf(unique.values());
        return values.stream().skip(Math.max(0, values.size() - 15L)).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rejectedFingerprints(String runId) {
        return softRejectedExamples(runId, "");
    }

    private List<Map<String, Object>> fingerprintsFromAttempts(List<ConceptSlot> sourceSlots) {
        LinkedHashMap<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (ConceptSlot sourceSlot : sourceSlots) {
            for (ConceptAttempt attempt : attempts.findAllBySlotIdOrderByAttemptNumber(sourceSlot.getId())) {
                if (attempt.getPhase() == ConceptAttemptPhase.LEGAL_REVIEW || attempt.getResultJson() == null) continue;
                JsonNode candidate = mapper.readTree(attempt.getResultJson());
                ConceptFingerprint.Value fingerprint = ConceptFingerprint.from(candidate);
                unique.putIfAbsent(fingerprint.canonicalHash(), ConceptFingerprint.businessSummary(candidate));
            }
        }
        return List.copyOf(unique.values());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> replacementFeedback(String runId, String slotId, String attemptId,
            JsonNode candidate, ConceptAttemptError reason, int nextRound) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        List<ConflictCandidate> conflicts = new ArrayList<>();
        for (Concept concept : concepts.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotSlotNumber(
                runId, run.getProject().getId())) {
            conflicts.add(new ConflictCandidate("ELIGIBLE_CONCEPT", concept.getSlot().getId(),
                mapper.readTree(concept.getCandidateJson())));
        }
        for (ConceptAttempt previous : attempts.findAllBySlotIdOrderByAttemptNumber(slotId)) {
            if (previous.getId().equals(attemptId) || previous.getPhase() == ConceptAttemptPhase.LEGAL_REVIEW
                    || previous.getResultJson() == null) continue;
            conflicts.add(new ConflictCandidate("CURRENT_SLOT_HISTORY", slotId,
                mapper.readTree(previous.getResultJson())));
        }
        ConflictCandidate closest = null;
        ConceptFingerprint.DistinctnessEvaluation evaluation = null;
        double best = -1;
        for (ConflictCandidate conflict : conflicts) {
            ConceptFingerprint.DistinctnessEvaluation current = ConceptFingerprint.evaluate(
                candidate, conflict.candidate(), slot.getVariationFocus());
            double score = current.focusSimilarity() + current.mechanicsSimilarity();
            if (score > best) {
                best = score;
                closest = conflict;
                evaluation = current;
            }
        }
        List<String> required = evaluation == null
            ? ConceptFingerprint.focusFieldNames(slot.getVariationFocus()).stream().limit(2).toList()
            : evaluation.requiredChangeDimensions();
        DistinctnessEvaluation distinctness = new DistinctnessEvaluation(
            evaluation == null ? ConceptFingerprint.Classification.DISTINCT : evaluation.classification(),
            closest == null ? rejectionScope(reason) : closest.source(),
            closest == null ? null : ConceptFingerprint.businessSummary(closest.candidate()),
            closest == null ? null : closest.slotId(),
            evaluation == null ? 0 : evaluation.focusSimilarity(),
            evaluation == null ? 0 : evaluation.mechanicsSimilarity(),
            evaluation == null ? List.of() : evaluation.overlappingDimensions(),
            evaluation == null ? List.of() : evaluation.materiallyDifferentDimensions(),
            required);
        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("round", nextRound);
        feedback.put("previousCandidate", ConceptFingerprint.businessSummary(candidate));
        feedback.put("rejectionReason", reason.name());
        feedback.put("conflictSource", distinctness.conflictScope());
        feedback.put("closestConflict", distinctness.conflictingCandidate());
        feedback.put("overlappingDimensions", distinctness.overlappingDimensions());
        feedback.put("materiallyDifferentDimensions", distinctness.materiallyDifferentDimensions());
        feedback.put("mustChangeDimensions", distinctness.requiredChangeDimensions());
        feedback.put("safeCorrectionInstruction",
            "LOCKED 원본 조건은 유지하고 지정된 두 개 이상의 축에서 사업 작동 방식을 실질적으로 변경하세요.");
        return Collections.unmodifiableMap(feedback);
    }

    private String rejectionScope(ConceptAttemptError reason) {
        return reason.name().startsWith("LEGAL_") ? "LEGAL_REVIEW" : "CANDIDATE_VALIDATION";
    }

    public record DistinctnessEvaluation(ConceptFingerprint.Classification classification,
            String conflictScope, Map<String, Object> conflictingCandidate, String conflictingSlotId,
            double focusSimilarity, double mechanicsSimilarity, List<String> overlappingDimensions,
            List<String> materiallyDifferentDimensions, List<String> requiredChangeDimensions) {}

    private record ConflictCandidate(String source, String slotId, JsonNode candidate) {}

    private void rejectCandidate(ConceptSlot slot, String attemptId, JsonNode candidate, ConceptAttemptError error,
            String safeCode, String safeSummary) {
        attempts.findById(attemptId).orElseThrow().reject(error, safeCode, mapper.writeValueAsString(candidate));
        if (slot.getStatus() != ConceptSlotStatus.REPLACING) slot.transitionTo(ConceptSlotStatus.REPLACING);
        saveRejectionSummary(slot, attemptId, error.name(), safeSummary);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void discardCandidate(String slotId, String attemptId, ConceptAttemptError error, String safeSummary) {
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        ConceptAttempt attempt = attempts.findById(attemptId).orElseThrow();
        if (rejections.existsByAttemptId(attemptId)) return;
        if (attempt.getResultJson() == null) {
            throw new IllegalStateException("discarded candidate requires a persisted candidate result");
        }
        attempt.reject(error, error.name(), attempt.getResultJson());
        if (slot.getStatus() != ConceptSlotStatus.REPLACING) slot.transitionTo(ConceptSlotStatus.REPLACING);
        rejections.save(ConceptRejectionSummary.create(slot, attemptId, error.name(), safeSummary));
    }

    private void saveRejectionSummary(ConceptSlot slot, String attemptId, String reasonCode, String safeSummary) {
        if (!rejections.existsByAttemptId(attemptId)) {
            rejections.save(ConceptRejectionSummary.create(slot, attemptId, reasonCode, safeSummary));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LegalDisposition legal(String runId, String slotId, String attemptId, JsonNode candidate, JsonNode legal) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        ConceptLegalStatus status = ConceptLegalStatus.valueOf(legal.path("status").asText());
        ConceptLegalFactPatternMapper.Result reviewedPattern = legalFactPattern(candidate);
        if (!"2.0".equals(legal.path("reviewedFactPatternSchemaVersion").asText())
            || !reviewedPattern.factPatternHash().equals(legal.path("reviewedFactPatternHash").asText())) {
            throw new IllegalStateException("legal review fact pattern does not match candidate");
        }
        LegalContextPack pack = contexts.findByProjectIdAndSourceSnapshotIdAndDeletedAtIsNull(
            run.getProject().getId(), run.getSourceIdeaBriefSnapshotId()).orElseThrow();
        Map<Integer, LegalEvidence> official = persistEvidence(pack, legal.path("officialEvidence"));
        if (status == ConceptLegalStatus.NEEDS_FACTS) {
            attempts.findById(attemptId).orElseThrow().succeed(mapper.writeValueAsString(legal));
            return LegalDisposition.NEEDS_INPUT;
        }
        if (status == ConceptLegalStatus.REDESIGNABLE) {
            validateFindingCoverage(legal, official.keySet(), false);
            attempts.findById(attemptId).orElseThrow().reject(ConceptAttemptError.LEGAL_REDESIGN_REQUIRED,
                "REDESIGN_REQUIRED", mapper.writeValueAsString(legal));
            slot.transitionTo(ConceptSlotStatus.REDESIGNING); return LegalDisposition.REDESIGN;
        }
        if (status == ConceptLegalStatus.REJECTED) {
            validateFindingCoverage(legal, official.keySet(), false);
            attempts.findById(attemptId).orElseThrow().reject(ConceptAttemptError.LEGAL_REJECTED,
                "LEGAL_REJECTED", mapper.writeValueAsString(legal));
            slot.transitionTo(ConceptSlotStatus.REJECTED); slot.transitionTo(ConceptSlotStatus.REPLACING);
            saveRejectionSummary(slot, attemptId, "LEGAL_REJECTED",
                legal.path("safeUserSummary").asText("재설계가 필요합니다."));
            return LegalDisposition.REPLACE;
        }
        validateFindingCoverage(legal, official.keySet(), true);
        String candidateJson = mapper.writeValueAsString(candidate);
        ConceptFingerprint.Value fingerprint = ConceptFingerprint.from(candidate);
        Concept concept = concepts.save(Concept.eligible(run, slot, candidate.path("conceptName").asText(),
            candidate.path("conceptDefinition").asText(), fingerprint.canonicalHash(), fingerprint.majorFieldHash(), status, candidateJson,
            mapper.writeValueAsString(Map.of("sourceSnapshotId", run.getSourceIdeaBriefSnapshotId(), "slotId", slotId, "attemptId", attemptId))));
        var safeAssessment = legal.deepCopy();
        if (safeAssessment.isObject()) {
            var safeObject = (tools.jackson.databind.node.ObjectNode) safeAssessment;
            safeObject.remove("officialEvidence");
            safeObject.set("legalFactPattern", reviewedPattern.factPattern());
        }
        ConceptLegalAssessment assessment = assessments.save(ConceptLegalAssessment.create(concept, pack, status,
            legal.path("safeUserSummary").asText(), mapper.writeValueAsString(safeAssessment),
            mapper.writeValueAsString(Map.of("contextPackId", pack.getId(), "sourceSnapshotHash",
                pack.getSourceSnapshotHash(), "registryVersion", pack.getRegistryVersion(),
                "reviewedAt", legal.path("reviewBasisDate").asText(),
                "factPatternSchemaVersion", "2.0",
                "factPatternHash", reviewedPattern.factPatternHash()))));
        for (JsonNode ref : legal.path("evidenceReferenceIndexes")) {
            LegalEvidence cited = official.get(ref.asInt(-1));
            if (cited == null) throw new IllegalStateException("invalid official evidence reference");
            evidenceLinks.save(ConceptLegalEvidenceLink.create(assessment, cited));
        }
        slot.transitionTo(ConceptSlotStatus.ELIGIBLE);
        attempts.findById(attemptId).orElseThrow().succeed(mapper.writeValueAsString(legal));
        return LegalDisposition.ELIGIBLE;
    }

    private Map<Integer, LegalEvidence> persistEvidence(LegalContextPack pack, JsonNode values) {
        if (!values.isArray()) throw new IllegalStateException("official evidence collection is invalid");
        Map<Integer, LegalEvidence> result = new LinkedHashMap<>();
        for (JsonNode value : values) {
            int index = value.path("referenceIndex").asInt(-1);
            if (index < 0 || result.containsKey(index)) throw new IllegalStateException("official evidence index is invalid");
            if (!"OFFICIAL_LAW".equals(value.path("sourceType").asText())
                || !"KR".equals(value.path("jurisdiction").asText())
                || !pack.getRegistryVersion().equals(value.path("registryVersion").asText())) {
                throw new IllegalStateException("official evidence source metadata is invalid");
            }
            String queryKey = requiredText(value, "queryKey");
            String article = requiredText(value, "articleReference");
            String contentHash = requiredText(value, "contentHash");
            LegalEvidence stored = evidence.findByContextPackIdAndQueryKeyAndArticleReferenceAndContentHashAndDeletedAtIsNull(
                pack.getId(), queryKey, article, contentHash).orElseGet(() -> evidence.save(LegalEvidence.officialLaw(
                    pack, optionalText(value, "lawId"), requiredText(value, "officialIdentifier"),
                    requiredText(value, "lawName"), article, value.path("title").asText(""),
                    requiredText(value, "officialSourceUri"), optionalText(value, "promulgationDate"),
                    optionalText(value, "effectiveDate"), OffsetDateTime.parse(requiredText(value, "retrievedAt")).toLocalDateTime(),
                    contentHash, requiredText(value, "boundedProvisionSummary"), queryKey,
                    requiredText(value, "registryVersion"))));
            result.put(index, stored);
        }
        return result;
    }

    private void validateFindingCoverage(JsonNode legal, Set<Integer> evidenceIndexes, boolean eligible) {
        if (eligible && (evidenceIndexes.isEmpty() || !legal.path("evidenceReferenceIndexes").isArray()
            || legal.path("evidenceReferenceIndexes").isEmpty())) {
            throw new IllegalStateException("eligible legal review requires official evidence");
        }
        Set<String> expected = new HashSet<>();
        for (String field : List.of("requiredControls", "requiredPartnersAndQualifications", "requiredDisclosures", "prohibitedVariants")) {
            JsonNode findings = legal.path(field);
            if (!findings.isArray()) throw new IllegalStateException("material legal finding collection is invalid");
            for (int i = 0; i < findings.size(); i++) expected.add(field + ":" + i);
        }
        Set<String> actual = new HashSet<>();
        for (JsonNode coverage : legal.path("findingEvidence")) {
            String key = requiredText(coverage, "findingType") + ":" + coverage.path("findingIndex").asInt(-1);
            if (!actual.add(key) || !coverage.path("evidenceReferenceIndexes").isArray()
                || coverage.path("evidenceReferenceIndexes").isEmpty()) throw new IllegalStateException("finding evidence coverage is invalid");
            for (JsonNode index : coverage.path("evidenceReferenceIndexes")) {
                if (!evidenceIndexes.contains(index.asInt(-1))) throw new IllegalStateException("finding evidence index is invalid");
            }
        }
        if (!actual.equals(expected)) throw new IllegalStateException("each material finding requires evidence");
    }

    private String requiredText(JsonNode value, String field) {
        String text = value.path(field).asText();
        if (text.isBlank()) throw new IllegalStateException("official evidence field is required");
        return text;
    }

    private String optionalText(JsonNode value, String field) {
        String text = value.path(field).asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failSlot(String runId, String slotId, String attemptId, ConceptAttemptError error, boolean retryable, boolean permanent) {
        if (attemptId != null) attempts.findById(attemptId).ifPresent(value -> terminalizeAttempt(value, error, error.name(), retryable));
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        slot.fail();
        if (permanent) {
            ConceptFactoryRun run = runs.findById(runId).orElseThrow();
            run.transitionTo(ConceptFactoryRunStatus.FAILED);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttemptError(String runId, String slotId, String attemptId, ConceptAttemptError error, boolean retryable) {
        recordAttemptError(runId, slotId, attemptId, error, error.name(), retryable);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttemptError(String runId, String slotId, String attemptId, ConceptAttemptError error,
            String safeErrorCode, boolean retryable) {
        terminalizeAttempt(attempts.findById(attemptId).orElseThrow(), error, safeErrorCode, retryable);
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        if (error == ConceptAttemptError.SCHEMA_INVALID && slot.getStatus() == ConceptSlotStatus.GENERATING) {
            slot.transitionTo(ConceptSlotStatus.SCHEMA_INVALID);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCandidateExhaustion(String attemptId, ConceptAttemptError error) {
        ConceptAttempt attempt = attempts.findById(attemptId).orElseThrow();
        if (attempt.getErrorClassification() == null) {
            terminalizeAttempt(attempt, error, error.name(), false);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failLegalReview(String runId, String slotId, String attemptId,
            ConceptAttemptError error, String safeErrorCode, boolean retryable) {
        terminalizeAttempt(attempts.findById(attemptId).orElseThrow(), error, safeErrorCode, retryable);
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        if (slot.getStatus() == ConceptSlotStatus.VALIDATING_LEGAL) {
            slot.transitionTo(ConceptSlotStatus.REVIEW_RETRY_PENDING);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void beginReplacement(String runId, String slotId, int replacementRound) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        slot.ensureReplacementRound(replacementRound);
        if (slot.getStatus() != ConceptSlotStatus.REPLACING) slot.transitionTo(ConceptSlotStatus.REPLACING);
        if (run.getStatus() == ConceptFactoryRunStatus.VALIDATING) run.ensureReplacementRound(replacementRound);
        if (run.getStatus() == ConceptFactoryRunStatus.REPLACING) {
            run.transitionTo(ConceptFactoryRunStatus.GENERATING);
            run.transitionTo(ConceptFactoryRunStatus.VALIDATING);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void needsInput(String runId) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        if (run.getStatus() != ConceptFactoryRunStatus.NEEDS_INPUT) run.transitionTo(ConceptFactoryRunStatus.NEEDS_INPUT);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failRun(String runId) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        if (run.getStatus() != ConceptFactoryRunStatus.FAILED) run.transitionTo(ConceptFactoryRunStatus.FAILED);
    }

    @Transactional(readOnly = true)
    public FailureDiagnostic diagnostic(String runId, String slotId) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        ConceptAttempt latest = attempts.findFirstBySlotIdOrderByAttemptNumberDesc(slotId).orElse(null);
        return new FailureDiagnostic(run.getStatus().name(), slot.getStatus().name(),
            latest == null ? null : latest.getPhase().name(),
            latest == null || latest.getSafeErrorCode() == null ? "INTERNAL_STATE_FAILURE" : latest.getSafeErrorCode());
    }

    @Transactional(readOnly = true)
    public AttemptTrace attemptTrace(String attemptId) {
        ConceptAttempt attempt = attempts.findById(attemptId).orElseThrow();
        return new AttemptTrace(attempt.getAttemptNumber(), attempt.getPhase().name(),
            attempt.getErrorClassification() == null ? null : attempt.getErrorClassification().name(),
            attempt.getSafeErrorCode(), attempt.isRetryable());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failActiveAttempt(String slotId, ConceptAttemptError error, String safeErrorCode) {
        attempts.findFirstBySlotIdOrderByAttemptNumberDesc(slotId).ifPresent(attempt -> {
            if (attempt.getResultJson() == null && attempt.getErrorClassification() == null) {
                attempt.fail(error, safeErrorCode, false);
            }
        });
    }

    @Transactional(readOnly = true)
    public String failureCode(String runId) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        return slots.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotNumber(
                runId, run.getProject().getId()).stream()
            .flatMap(slot -> attempts.findAllBySlotIdOrderByAttemptNumber(slot.getId()).stream())
            .filter(attempt -> attempt.getErrorClassification() != null)
            .max(Comparator.comparing(ConceptAttempt::getUpdatedAt,
                Comparator.nullsFirst(Comparator.naturalOrder())))
            .map(attempt -> attempt.getSafeErrorCode() == null
                ? attempt.getErrorClassification().name() : attempt.getSafeErrorCode())
            .orElse("INTERNAL_STATE_FAILURE");
    }

    private void terminalizeAttempt(ConceptAttempt attempt, ConceptAttemptError error,
            String safeErrorCode, boolean retryable) {
        if (attempt.getResultJson() == null) attempt.fail(error, safeErrorCode, retryable);
        else attempt.reject(error, safeErrorCode, attempt.getResultJson());
    }

    @Transactional
    public boolean completeIfEligible(String runId) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        List<ConceptSlot> allSlots = slots.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotNumber(runId, run.getProject().getId());
        List<Concept> allConcepts = concepts.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotSlotNumber(runId, run.getProject().getId());
        if (allSlots.stream().allMatch(value -> value.getStatus() == ConceptSlotStatus.ELIGIBLE)) {
            ConceptFactoryCompletionPolicy.complete(run, allSlots, allConcepts, mapper); return true;
        }
        return false;
    }

    public enum LegalDisposition { ELIGIBLE, REDESIGN, REPLACE, NEEDS_INPUT }
    public enum CandidateDisposition { ACCEPTED, ORIGIN_INVALID, LOCKED_INVALID, DUPLICATE,
        SEMANTIC_REVIEW_REQUIRED, PROVIDER_RETRY_LATER, PROVIDER_PERMANENT_FAILURE,
        REQUEST_CONTRACT_FAILURE, PROVIDER_FATAL_FAILURE }
    public record FailureDiagnostic(String runStatus, String slotStatus, String phase, String safeErrorCode) {}
    public record AttemptTrace(int attemptNumber, String phase, String errorClassification,
                               String safeErrorCode, boolean retryable) {}
    public record SlotWork(String slotId, int slotNumber, VariationFocus focus, int redesignCount,
                           int replacementRounds,
                           String candidateAttemptId, String candidateJson) {
        public SlotWork(String slotId, int slotNumber, VariationFocus focus, int redesignCount) {
            this(slotId, slotNumber, focus, redesignCount, 0, null, null);
        }
    }
    public record Work(String runId, Long projectId, String snapshotId, ConceptGenerationStrategy generationStrategy,
                       List<Map<String, String>> fields,
                       Map<String, Object> externalFactContext, List<SlotWork> slots) {}
}
