package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.concept.application.ConceptFactoryExecutionService;
import com.aivle.backend.pipeline.concept.application.ConceptLegalFactPatternMapper;
import com.aivle.backend.pipeline.concept.domain.*;
import com.aivle.backend.pipeline.concept.repository.*;
import com.aivle.backend.pipeline.idea.repository.*;
import com.aivle.backend.pipeline.legal.application.CanonicalLegalContextAssembler;
import com.aivle.backend.pipeline.legal.repository.*;
import com.aivle.backend.project.entity.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConceptFactoryFingerprintHistoryTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptedRejectedAndCurrentSlotHistoryUseTheSameFullBusinessFingerprint() throws Exception {
        ConceptFactoryRunRepository runs = mock(ConceptFactoryRunRepository.class);
        ConceptSlotRepository slots = mock(ConceptSlotRepository.class);
        ConceptAttemptRepository attempts = mock(ConceptAttemptRepository.class);
        ConceptRepository concepts = mock(ConceptRepository.class);
        ConceptFactoryExecutionService service = service(runs, slots, attempts, concepts,
            mock(ConceptRejectionSummaryRepository.class));
        String candidateJson = Files.readString(
            Path.of("../contracts/concept/business-fingerprint-v1.json"));
        ConceptFactoryRun run = mock(ConceptFactoryRun.class);
        Project project = mock(Project.class);
        ConceptSlot slot = mock(ConceptSlot.class);
        ConceptAttempt attempt = mock(ConceptAttempt.class);
        Concept concept = mock(Concept.class);
        when(run.getProject()).thenReturn(project);
        when(project.getId()).thenReturn(41L);
        when(runs.findById("run-1")).thenReturn(Optional.of(run));
        when(slot.getId()).thenReturn("slot-1");
        when(slots.findById("slot-1")).thenReturn(Optional.of(slot));
        when(slots.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotNumber("run-1", 41L))
            .thenReturn(List.of(slot));
        when(attempt.getPhase()).thenReturn(ConceptAttemptPhase.INITIAL);
        when(attempt.getResultJson()).thenReturn(candidateJson);
        when(attempts.findAllBySlotIdOrderByAttemptNumber("slot-1")).thenReturn(List.of(attempt));
        when(concept.getCandidateJson()).thenReturn(candidateJson);
        when(concepts.findAllByRunIdAndProjectIdAndDeletedAtIsNullOrderBySlotSlotNumber("run-1", 41L))
            .thenReturn(List.of(concept));

        Map<String, Object> expected = ConceptFingerprint.businessSummary(mapper.readTree(candidateJson));

        assertThat(service.acceptedFingerprints("run-1")).containsExactly(expected);
        assertThat(service.rejectedFingerprints("run-1")).containsExactly(expected);
        assertThat(service.currentSlotPreviousFingerprints("slot-1")).containsExactly(expected);
    }

    @Test
    void discardCandidatePersistsExactlyOneCanonicalSummaryAndProviderFailureDoesNotUseThisPath() {
        ConceptSlotRepository slots = mock(ConceptSlotRepository.class);
        ConceptAttemptRepository attempts = mock(ConceptAttemptRepository.class);
        ConceptRejectionSummaryRepository rejections = mock(ConceptRejectionSummaryRepository.class);
        ConceptFactoryExecutionService service = service(mock(ConceptFactoryRunRepository.class), slots,
            attempts, mock(ConceptRepository.class), rejections);
        ConceptSlot slot = mock(ConceptSlot.class);
        ConceptAttempt attempt = mock(ConceptAttempt.class);
        when(slots.findById("slot-1")).thenReturn(Optional.of(slot));
        when(attempts.findById("attempt-1")).thenReturn(Optional.of(attempt));
        when(attempt.getResultJson()).thenReturn("{\"conceptName\":\"폐기 후보\"}");
        when(slot.getStatus()).thenReturn(ConceptSlotStatus.REDESIGNING);
        when(rejections.existsByAttemptId("attempt-1")).thenReturn(false, true);

        service.discardCandidate("slot-1", "attempt-1",
            ConceptAttemptError.LEGAL_REDESIGN_EXHAUSTED, "재설계 예산 소진");
        service.discardCandidate("slot-1", "attempt-1",
            ConceptAttemptError.LEGAL_REDESIGN_EXHAUSTED, "재설계 예산 소진");

        verify(attempt, times(1)).reject(ConceptAttemptError.LEGAL_REDESIGN_EXHAUSTED,
            "LEGAL_REDESIGN_EXHAUSTED", "{\"conceptName\":\"폐기 후보\"}");
        verify(rejections, times(1)).save(any(ConceptRejectionSummary.class));
    }

    private ConceptFactoryExecutionService service(ConceptFactoryRunRepository runs,
            ConceptSlotRepository slots, ConceptAttemptRepository attempts, ConceptRepository concepts,
            ConceptRejectionSummaryRepository rejections) {
        return new ConceptFactoryExecutionService(
            runs, slots, attempts, concepts, mock(IdeaBriefFieldRepository.class),
            mock(IdeaBriefRepository.class), mock(LegalContextPackRepository.class),
            mock(LegalEvidenceRepository.class), mock(ConceptLegalAssessmentRepository.class),
            mock(ConceptLegalEvidenceLinkRepository.class), rejections, mapper,
            mock(CanonicalLegalContextAssembler.class), mock(ConceptLegalFactPatternMapper.class));
    }
}
