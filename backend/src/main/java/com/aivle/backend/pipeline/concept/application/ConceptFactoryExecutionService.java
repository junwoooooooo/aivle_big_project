package com.aivle.backend.pipeline.concept.application;

import com.aivle.backend.pipeline.concept.domain.*;
import com.aivle.backend.pipeline.concept.repository.*;
import com.aivle.backend.pipeline.idea.domain.IdeaBriefField;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
import com.aivle.backend.pipeline.legal.domain.*;
import com.aivle.backend.pipeline.legal.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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

    @Transactional
    public Work prepare(String runId, Long projectId) {
        ConceptFactoryRun run = runs.findById(runId).filter(v -> v.getProject().getId().equals(projectId)).orElseThrow();
        if (run.getStatus() == ConceptFactoryRunStatus.QUEUED) run.transitionTo(ConceptFactoryRunStatus.GENERATING);
        if (run.getStatus() == ConceptFactoryRunStatus.GENERATING) run.transitionTo(ConceptFactoryRunStatus.VALIDATING);
        LegalContextPack pack = contexts.findByProjectIdAndSourceSnapshotIdAndDeletedAtIsNull(projectId, run.getSourceIdeaBriefSnapshotId())
            .orElseGet(() -> createContext(run));
        List<LegalEvidence> official = evidence.findAllByContextPackIdAndProjectIdAndDeletedAtIsNull(pack.getId(), projectId);
        if (official.isEmpty()) throw new IllegalStateException("official legal evidence is required");
        List<Map<String, String>> fields = ideaFields.findAllByBriefIdOrderById(run.getSourceIdeaBriefSnapshotId()).stream()
            .map(value -> Map.of("fieldKey", value.getFieldKey(), "value", Objects.toString(value.getFieldValue(), ""))).toList();
        Map<String, Object> shared = sharedContext(pack, official);
        List<SlotWork> slotWork = slots.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotNumber(runId, projectId).stream()
            .filter(value -> value.getStatus() != ConceptSlotStatus.ELIGIBLE)
            .map(value -> new SlotWork(value.getId(), value.getSlotNumber(), value.getVariationFocus(), value.getLegalRedesignCount())).toList();
        return new Work(runId, projectId, run.getSourceIdeaBriefSnapshotId(), fields, shared, slotWork);
    }

    private LegalContextPack createContext(ConceptFactoryRun run) {
        Map<String, String> values = new HashMap<>();
        for (IdeaBriefField field : ideaFields.findAllByBriefIdOrderById(run.getSourceIdeaBriefSnapshotId()))
            values.put(field.getFieldKey(), Objects.toString(field.getFieldValue(), "미확인"));
        LegalContextPack pack = LegalContextPack.pending(run.getProject(), run.getSourceIdeaBriefSnapshotId(), run.getSourceSnapshotHash());
        pack.complete(values.getOrDefault("industry", "미확인"), values.getOrDefault("targetRegion", "대한민국"),
            values.getOrDefault("platformRole", "미확인"), values.getOrDefault("transactionFlow", "미확인"),
            values.getOrDefault("payment", "미확인"), values.getOrDefault("personalData", "미확인"),
            mapper.writeValueAsString(List.of(values.getOrDefault("physicalActivity", "미확인"))),
            mapper.writeValueAsString(List.of(values.getOrDefault("requiredPartners", "미확인"))),
            mapper.writeValueAsString(List.of(values.getOrDefault("labelingAndAdvertising", "미확인"))));
        contexts.save(pack);
        evidence.save(LegalEvidence.create(pack, "국가법령정보센터 공식 법령 근거", "https://www.law.go.kr/",
            ConceptCanonicalizer.hash("https://www.law.go.kr/", run.getSourceSnapshotHash())));
        return pack;
    }

    private Map<String, Object> sharedContext(LegalContextPack pack, List<LegalEvidence> values) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) refs.add(Map.of("referenceIndex", i, "title", values.get(i).getTitle(),
            "officialSourceUri", values.get(i).getSourceUri(), "reviewedAt", java.time.LocalDate.now().toString()));
        return Map.of("industry", pack.getIndustry(), "region", pack.getRegion(), "platformRole", pack.getPlatformRole(),
            "transactionStructure", pack.getTransactionStructure(), "payment", pack.getPayment(), "personalData", pack.getPersonalData(),
            "physicalActivities", mapper.readTree(pack.getPhysicalActivitiesJson()),
            "qualificationsAndPermits", mapper.readTree(pack.getQualificationsAndPermitsJson()),
            "labelingAndAdvertising", mapper.readTree(pack.getLabelingAndAdvertisingJson()), "officialEvidence", refs);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String beginAttempt(String slotId, ConceptAttemptPhase phase, String taskRunId) {
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        if (slot.getStatus() != ConceptSlotStatus.GENERATING) slot.transitionTo(ConceptSlotStatus.GENERATING);
        return attempts.save(ConceptAttempt.begin(slot, phase, taskRunId)).getId();
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
        if (status == ConceptLegalStatus.NEEDS_FACTS) {
            attempts.findById(attemptId).orElseThrow().fail(ConceptAttemptError.INSUFFICIENT_INFORMATION, "NEEDS_FACTS", false);
            slot.transitionTo(ConceptSlotStatus.NEEDS_INPUT); run.transitionTo(ConceptFactoryRunStatus.NEEDS_INPUT);
            return LegalDisposition.NEEDS_INPUT;
        }
        if (status == ConceptLegalStatus.REDESIGNABLE) {
            attempts.findById(attemptId).orElseThrow().fail(ConceptAttemptError.LEGAL_REDESIGN_REQUIRED, "REDESIGN_REQUIRED", false);
            slot.transitionTo(ConceptSlotStatus.REDESIGNING); return LegalDisposition.REDESIGN;
        }
        if (status == ConceptLegalStatus.REJECTED) {
            attempts.findById(attemptId).orElseThrow().fail(ConceptAttemptError.LEGAL_REJECTED, "LEGAL_REJECTED", false);
            slot.transitionTo(ConceptSlotStatus.REJECTED); slot.transitionTo(ConceptSlotStatus.REPLACING);
            rejections.save(ConceptRejectionSummary.create(slot, "LEGAL_REJECTED", legal.path("safeUserSummary").asText("재설계가 필요합니다.")));
            return LegalDisposition.REPLACE;
        }
        String candidateJson = mapper.writeValueAsString(candidate);
        String canonical = ConceptCanonicalizer.hash(candidateJson);
        String major = ConceptCanonicalizer.hash(candidate.path("targetSegment").asText(), candidate.path("valueProposition").asText(), candidate.path("solutionMechanism").asText());
        Concept concept = concepts.save(Concept.eligible(run, slot, candidate.path("conceptName").asText(),
            candidate.path("oneLineSummary").asText(), canonical, major, status, candidateJson,
            mapper.writeValueAsString(Map.of("sourceSnapshotId", run.getSourceIdeaBriefSnapshotId(), "slotId", slotId, "attemptId", attemptId))));
        LegalContextPack pack = contexts.findByProjectIdAndSourceSnapshotIdAndDeletedAtIsNull(run.getProject().getId(), run.getSourceIdeaBriefSnapshotId()).orElseThrow();
        ConceptLegalAssessment assessment = assessments.save(ConceptLegalAssessment.create(concept, pack, status,
            legal.path("safeUserSummary").asText(), mapper.writeValueAsString(legal),
            mapper.writeValueAsString(Map.of("contextPackId", pack.getId(), "reviewedAt", legal.path("reviewBasisDate").asText()))));
        List<LegalEvidence> refs = evidence.findAllByContextPackIdAndProjectIdAndDeletedAtIsNull(pack.getId(), run.getProject().getId());
        for (JsonNode ref : legal.path("evidenceReferences")) {
            int index = ref.path("referenceIndex").asInt(-1);
            if (index < 0 || index >= refs.size()) throw new IllegalStateException("invalid official evidence reference");
            evidenceLinks.save(ConceptLegalEvidenceLink.create(assessment, refs.get(index)));
        }
        slot.transitionTo(ConceptSlotStatus.ELIGIBLE);
        return LegalDisposition.ELIGIBLE;
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
        attempts.findById(attemptId).orElseThrow().fail(error, error.name(), retryable);
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        if (error == ConceptAttemptError.SCHEMA_INVALID) {
            slot.transitionTo(ConceptSlotStatus.SCHEMA_INVALID);
        } else if (error == ConceptAttemptError.TRANSIENT_PROVIDER_FAILURE) {
            try {
                run.recordProviderTransientRetry();
            } catch (IllegalStateException exhausted) {
                slot.transitionTo(ConceptSlotStatus.REPLACING);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void beginReplacement(String runId, String slotId) {
        ConceptFactoryRun run = runs.findById(runId).orElseThrow();
        ConceptSlot slot = slots.findById(slotId).orElseThrow();
        if (slot.getStatus() != ConceptSlotStatus.REPLACING) slot.transitionTo(ConceptSlotStatus.REPLACING);
        if (run.getStatus() == ConceptFactoryRunStatus.VALIDATING) run.beginReplacementRound();
        if (run.getStatus() == ConceptFactoryRunStatus.REPLACING) {
            run.transitionTo(ConceptFactoryRunStatus.GENERATING);
            run.transitionTo(ConceptFactoryRunStatus.VALIDATING);
        }
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
    public record SlotWork(String slotId, int slotNumber, VariationFocus focus, int redesignCount) {}
    public record Work(String runId, Long projectId, String snapshotId, List<Map<String, String>> fields,
                       Map<String, Object> sharedContext, List<SlotWork> slots) {}
}
