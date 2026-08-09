package com.aivle.backend.pipeline.concept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.concept.application.*;
import com.aivle.backend.pipeline.concept.domain.*;
import com.aivle.backend.pipeline.concept.repository.*;
import com.aivle.backend.pipeline.idea.repository.*;
import com.aivle.backend.pipeline.legal.application.CanonicalLegalContextAssembler;
import com.aivle.backend.pipeline.legal.domain.LegalContextPack;
import com.aivle.backend.pipeline.legal.repository.*;
import com.aivle.backend.project.entity.Project;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConceptFactoryLegalNeedsFactsTests {
    @Test
    void needsFactsPausesForUserInputWithoutDiscardOrReplacement() {
        ConceptFactoryRunRepository runs = mock(ConceptFactoryRunRepository.class);
        ConceptSlotRepository slots = mock(ConceptSlotRepository.class);
        ConceptAttemptRepository attempts = mock(ConceptAttemptRepository.class);
        ConceptRejectionSummaryRepository rejections = mock(ConceptRejectionSummaryRepository.class);
        LegalContextPackRepository contexts = mock(LegalContextPackRepository.class);
        ConceptLegalFactPatternMapper patterns = mock(ConceptLegalFactPatternMapper.class);
        ObjectMapper mapper = new ObjectMapper();
        ConceptFactoryExecutionService service = new ConceptFactoryExecutionService(
            runs, slots, attempts, mock(ConceptRepository.class), mock(IdeaBriefFieldRepository.class),
            mock(IdeaBriefRepository.class), contexts, mock(LegalEvidenceRepository.class),
            mock(ConceptLegalAssessmentRepository.class), mock(ConceptLegalEvidenceLinkRepository.class),
            rejections, mapper, mock(CanonicalLegalContextAssembler.class), patterns);
        ConceptFactoryRun run = mock(ConceptFactoryRun.class); Project project = mock(Project.class);
        ConceptSlot slot = mock(ConceptSlot.class); ConceptAttempt attempt = mock(ConceptAttempt.class);
        LegalContextPack pack = mock(LegalContextPack.class);
        when(run.getProject()).thenReturn(project); when(project.getId()).thenReturn(41L);
        when(run.getSourceIdeaBriefSnapshotId()).thenReturn("brief-1");
        when(runs.findById("run-1")).thenReturn(Optional.of(run));
        when(slots.findById("slot-1")).thenReturn(Optional.of(slot));
        when(attempts.findById("attempt-1")).thenReturn(Optional.of(attempt));
        when(contexts.findByProjectIdAndSourceSnapshotIdAndDeletedAtIsNull(41L, "brief-1"))
            .thenReturn(Optional.of(pack));
        when(patterns.map(any())).thenReturn(new ConceptLegalFactPatternMapper.Result(
            mapper.readTree("{\"schemaVersion\":\"2.0\",\"jurisdiction\":\"KR\"}"),
            "sha256:" + "a".repeat(64)));
        var candidate = mapper.readTree("{\"schemaVersion\":\"2.0\",\"targetRegion\":\"대한민국\"}");
        var legal = mapper.readTree("""
            {"status":"NEEDS_FACTS","reviewedFactPatternSchemaVersion":"2.0",
             "reviewedFactPatternHash":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
             "officialEvidence":[],"safeUserSummary":"외부 사실 확인 필요"}
            """);

        var result = service.legal("run-1", "slot-1", "attempt-1", candidate, legal);

        assertThat(result).isEqualTo(ConceptFactoryExecutionService.LegalDisposition.NEEDS_INPUT);
        verify(attempt).succeed(contains("NEEDS_FACTS"));
        verify(slot, never()).transitionTo(ConceptSlotStatus.REPLACING);
        verify(run, never()).transitionTo(ConceptFactoryRunStatus.NEEDS_INPUT);
        verify(rejections, never()).save(any(ConceptRejectionSummary.class));
    }
}
