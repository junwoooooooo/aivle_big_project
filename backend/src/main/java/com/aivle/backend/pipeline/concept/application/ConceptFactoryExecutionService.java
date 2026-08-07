package com.aivle.backend.pipeline.concept.application;

import com.aivle.backend.pipeline.concept.domain.*;
import com.aivle.backend.pipeline.concept.repository.*;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.legal.application.CanonicalLegalContextAssembler;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
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
    private final LegalContextPackRepository contexts;
    private final LegalEvidenceRepository evidence;
    private final ConceptLegalAssessmentRepository assessments;
    private final ConceptLegalEvidenceLinkRepository evidenceLinks;
    private final ConceptRejectionSummaryRepository rejections;
    private final ObjectMapper mapper;
    private final CanonicalLegalContextAssembler legalContextAssembler;
    @Value("${app.legal.registry-version:legal-registry-v1}")
    private String registryVersion;

    @Transactional
    public Work prepare(String runId, Long projectId) {
        ConceptFactoryRun run = runs.findById(runId).filter(v -> v.getProject().getId().equals(projectId)).orElseThrow();
        if (run.getStatus() == ConceptFactoryRunStatus.QUEUED) run.transitionTo(ConceptFactoryRunStatus.GENERATING);
        if (run.getStatus() == ConceptFactoryRunStatus.GENERATING) run.transitionTo(ConceptFactoryRunStatus.VALIDATING);
        LegalContextPack pack = contexts.findByProjectIdAndSourceSnapshotIdAndDeletedAtIsNull(projectId, run.getSourceIdeaBriefSnapshotId())
            .orElseGet(() -> createContext(run));
        List<Map<String, String>> fields = ideaFields.findAllByBriefIdOrderById(run.getSourceIdeaBriefSnapshotId()).stream()
            .map(value -> Map.of("fieldKey", value.getFieldKey(), "value", Objects.toString(value.getFieldValue(), ""))).toList();
        Map<String, Object> shared = sharedContext(pack);
        List<SlotWork> slotWork = slots.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotNumber(runId, projectId).stream()
            .filter(value -> value.getStatus() != ConceptSlotStatus.ELIGIBLE)
            .map(value -> {
                ConceptAttempt candidate = attempts.findAllBySlotIdOrderByAttemptNumber(value.getId()).stream()
                    .filter(attempt -> attempt.getPhase() != ConceptAttemptPhase.LEGAL_REVIEW
                        && attempt.getResultJson() != null)
                    .reduce((first, second) -> second).orElse(null);
                return new SlotWork(value.getId(), value.getSlotNumber(), value.getVariationFocus(),
                    value.getLegalRedesignCount(), candidate == null ? null : candidate.getId(),
                    candidate == null ? null : candidate.getResultJson());
            }).toList();
        return new Work(runId, projectId, run.getSourceIdeaBriefSnapshotId(), fields, shared, slotWork);
    }

    private LegalContextPack createContext(ConceptFactoryRun run) {
        CanonicalLegalContextAssembler.Result assembled = legalContextAssembler.assemble(
            ideaFields.findAllByBriefIdOrderById(run.getSourceIdeaBriefSnapshotId()));
        return contexts.save(LegalContextPack.ready(run.getProject(), run.getSourceIdeaBriefSnapshotId(),
            run.getSourceSnapshotHash(), assembled.contextJson(), assembled.provenanceJson(), registryVersion));
    }

    private Map<String, Object> sharedContext(LegalContextPack pack) {
        return Map.of("sourceSnapshotHash", pack.getSourceSnapshotHash(),
            "registryVersion", pack.getRegistryVersion(),
            "fields", mapper.readTree(pack.getCanonicalContextJson()));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> sharedOfficialEvidence(String runId) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        LegalContextPack pack = contexts.findByProjectIdAndSourceSnapshotIdAndDeletedAtIsNull(
            run.getProject().getId(), run.getSourceIdeaBriefSnapshotId()).orElseThrow();
        List<LegalEvidence> values = evidence.findAllByContextPackIdAndProjectIdAndDeletedAtIsNull(
            pack.getId(), run.getProject().getId());
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            LegalEvidence value = values.get(index);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("referenceIndex", index);
            item.put("sourceType", value.getSourceType());
            item.put("lawId", value.getLawId());
            item.put("officialIdentifier", value.getOfficialIdentifier());
            item.put("lawName", value.getLawName());
            item.put("articleReference", value.getArticleReference());
            item.put("title", value.getTitle());
            item.put("officialSourceUri", value.getOfficialSourceUri());
            item.put("jurisdiction", value.getJurisdiction());
            item.put("promulgationDate", value.getPromulgationDate());
            item.put("effectiveDate", value.getEffectiveDate());
            item.put("retrievedAt", value.getRetrievedAt().toString());
            item.put("contentHash", value.getContentHash());
            item.put("boundedProvisionSummary", value.getBoundedProvisionSummary());
            item.put("queryKey", value.getQueryKey());
            item.put("registryVersion", value.getRegistryVersion());
            result.add(item);
        }
        return result;
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
    public void generated(String slotId, String attemptId, JsonNode candidate) {
        attempts.findById(attemptId).orElseThrow().succeed(mapper.writeValueAsString(candidate));
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        slot.transitionTo(ConceptSlotStatus.GENERATED);
        slot.transitionTo(ConceptSlotStatus.VALIDATING_ORIGIN);
        slot.transitionTo(ConceptSlotStatus.VALIDATING_LEGAL);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LegalDisposition legal(String runId, String slotId, String attemptId, JsonNode candidate, JsonNode legal) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        ConceptLegalStatus status = ConceptLegalStatus.valueOf(legal.path("status").asText());
        LegalContextPack pack = contexts.findByProjectIdAndSourceSnapshotIdAndDeletedAtIsNull(
            run.getProject().getId(), run.getSourceIdeaBriefSnapshotId()).orElseThrow();
        Map<Integer, LegalEvidence> official = persistEvidence(pack, legal.path("officialEvidence"));
        if (status == ConceptLegalStatus.NEEDS_FACTS) {
            attempts.findById(attemptId).orElseThrow().fail(ConceptAttemptError.NEEDS_FACTS, "NEEDS_FACTS", false);
            slot.transitionTo(ConceptSlotStatus.NEEDS_INPUT); run.transitionTo(ConceptFactoryRunStatus.NEEDS_INPUT);
            return LegalDisposition.NEEDS_INPUT;
        }
        if (status == ConceptLegalStatus.REDESIGNABLE) {
            validateFindingCoverage(legal, official.keySet(), false);
            attempts.findById(attemptId).orElseThrow().fail(ConceptAttemptError.LEGAL_REDESIGN_REQUIRED, "REDESIGN_REQUIRED", false);
            slot.transitionTo(ConceptSlotStatus.REDESIGNING); return LegalDisposition.REDESIGN;
        }
        if (status == ConceptLegalStatus.REJECTED) {
            validateFindingCoverage(legal, official.keySet(), false);
            attempts.findById(attemptId).orElseThrow().fail(ConceptAttemptError.LEGAL_REJECTED, "LEGAL_REJECTED", false);
            slot.transitionTo(ConceptSlotStatus.REJECTED); slot.transitionTo(ConceptSlotStatus.REPLACING);
            rejections.save(ConceptRejectionSummary.create(slot, "LEGAL_REJECTED", legal.path("safeUserSummary").asText("재설계가 필요합니다.")));
            return LegalDisposition.REPLACE;
        }
        validateFindingCoverage(legal, official.keySet(), true);
        String candidateJson = mapper.writeValueAsString(candidate);
        String canonical = ConceptCanonicalizer.hash(candidateJson);
        String major = ConceptCanonicalizer.hash(candidate.path("targetSegment").asText(), candidate.path("valueProposition").asText(), candidate.path("solutionMechanism").asText());
        Concept concept = concepts.save(Concept.eligible(run, slot, candidate.path("conceptName").asText(),
            candidate.path("oneLineSummary").asText(), canonical, major, status, candidateJson,
            mapper.writeValueAsString(Map.of("sourceSnapshotId", run.getSourceIdeaBriefSnapshotId(), "slotId", slotId, "attemptId", attemptId))));
        var safeAssessment = legal.deepCopy();
        if (safeAssessment.isObject()) ((tools.jackson.databind.node.ObjectNode) safeAssessment).remove("officialEvidence");
        ConceptLegalAssessment assessment = assessments.save(ConceptLegalAssessment.create(concept, pack, status,
            legal.path("safeUserSummary").asText(), mapper.writeValueAsString(safeAssessment),
            mapper.writeValueAsString(Map.of("contextPackId", pack.getId(), "sourceSnapshotHash",
                pack.getSourceSnapshotHash(), "registryVersion", pack.getRegistryVersion(),
                "reviewedAt", legal.path("reviewBasisDate").asText()))));
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
        if (attemptId != null) attempts.findById(attemptId).ifPresent(value -> value.fail(error, error.name(), retryable));
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
        attempts.findById(attemptId).orElseThrow().fail(error, safeErrorCode, retryable);
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        if (error == ConceptAttemptError.SCHEMA_INVALID && slot.getStatus() == ConceptSlotStatus.GENERATING) {
            slot.transitionTo(ConceptSlotStatus.SCHEMA_INVALID);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failLegalReview(String runId, String slotId, String attemptId,
            ConceptAttemptError error, String safeErrorCode, boolean retryable) {
        attempts.findById(attemptId).orElseThrow().fail(error, safeErrorCode, retryable);
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        if (slot.getStatus() == ConceptSlotStatus.VALIDATING_LEGAL) {
            slot.transitionTo(ConceptSlotStatus.REVIEW_RETRY_PENDING);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void beginReplacement(String runId, String slotId, int replacementRound) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
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

    @Transactional
    public boolean completeIfEligible(String runId) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        List<ConceptSlot> allSlots = slots.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotNumber(runId, run.getProject().getId());
        List<Concept> allConcepts = concepts.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotSlotNumber(runId, run.getProject().getId());
        if (allSlots.stream().allMatch(value -> value.getStatus() == ConceptSlotStatus.ELIGIBLE)) {
            ConceptFactoryCompletionPolicy.complete(run, allSlots, allConcepts); return true;
        }
        return false;
    }

    public enum LegalDisposition { ELIGIBLE, REDESIGN, REPLACE, NEEDS_INPUT }
    public record FailureDiagnostic(String runStatus, String slotStatus, String phase, String safeErrorCode) {}
    public record SlotWork(String slotId, int slotNumber, VariationFocus focus, int redesignCount,
                           String candidateAttemptId, String candidateJson) {
        public SlotWork(String slotId, int slotNumber, VariationFocus focus, int redesignCount) {
            this(slotId, slotNumber, focus, redesignCount, null, null);
        }
    }
    public record Work(String runId, Long projectId, String snapshotId, List<Map<String, String>> fields,
                       Map<String, Object> sharedContext, List<SlotWork> slots) {}
}
