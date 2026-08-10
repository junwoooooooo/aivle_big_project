package com.aivle.backend.pipeline.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.pipeline.concept.application.ConceptLegalFactPatternMapper;
import com.aivle.backend.pipeline.concept.domain.Concept;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRun;
import com.aivle.backend.pipeline.concept.domain.ConceptFactoryRunStatus;
import com.aivle.backend.pipeline.concept.repository.ConceptFactoryRunRepository;
import com.aivle.backend.pipeline.concept.repository.ConceptRepository;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.pipeline.legal.domain.ConceptLegalAssessment;
import com.aivle.backend.pipeline.legal.domain.LegalContextPack;
import com.aivle.backend.pipeline.legal.application.LegalJurisdictionResolver;
import com.aivle.backend.pipeline.legal.repository.ConceptLegalAssessmentRepository;
import com.aivle.backend.pipeline.selection.api.SelectionApiModels.HypothesisAction;
import com.aivle.backend.pipeline.selection.api.SelectionApiModels.HypothesisActionRequest;
import com.aivle.backend.pipeline.selection.api.SelectionApiModels.CreateSelectionRequest;
import com.aivle.backend.pipeline.selection.application.ConceptSelectionService;
import com.aivle.backend.pipeline.selection.domain.*;
import com.aivle.backend.pipeline.selection.repository.ConceptHypothesisDecisionRepository;
import com.aivle.backend.pipeline.selection.repository.ConceptSelectionRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.domain.TaskRunState;
import com.aivle.backend.taskrun.service.CanonicalInputHasher;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.user.entity.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class ConceptSelectionServiceV2Tests {
    @Test
    void selectionCreatesSevenDecisionsIncludingLockedTargetRegion() {
        ObjectMapper mapper = new ObjectMapper();
        ProjectRepository projects = mock(ProjectRepository.class);
        ConceptFactoryRunRepository runs = mock(ConceptFactoryRunRepository.class);
        ConceptRepository concepts = mock(ConceptRepository.class);
        ConceptLegalAssessmentRepository assessments = mock(ConceptLegalAssessmentRepository.class);
        ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
        ConceptHypothesisDecisionRepository decisions = mock(ConceptHypothesisDecisionRepository.class);
        ConceptSelectionService service = new ConceptSelectionService(projects, runs, concepts, assessments,
            selections, decisions, mock(ConceptLegalFactPatternMapper.class), mapper,
            new LegalJurisdictionResolver(), mock(TaskRunService.class), mock(CanonicalInputHasher.class),
            mock(JobEventPublisher.class));
        Project project = mock(Project.class); User owner = mock(User.class); ConceptFactoryRun run = mock(ConceptFactoryRun.class);
        Concept concept = mock(Concept.class); List<ConceptHypothesisDecision> saved = new ArrayList<>();
        when(owner.getId()).thenReturn(7L); when(project.getOwner()).thenReturn(owner);
        when(projects.findByIdForUpdate(41L)).thenReturn(Optional.of(project));
        when(runs.findCurrentOwned(7L, 41L)).thenReturn(Optional.of(run));
        when(run.getStatus()).thenReturn(ConceptFactoryRunStatus.COMPLETED); when(run.getId()).thenReturn("run-1");
        when(run.getSourceSnapshotHash()).thenReturn("sha256:" + "b".repeat(64));
        when(concepts.findByIdAndProjectIdAndPublishedTrueAndDeletedAtIsNull("concept-1", 41L)).thenReturn(Optional.of(concept));
        when(concept.getId()).thenReturn("concept-1"); when(concept.getRun()).thenReturn(run);
        when(concept.getSourceSnapshotHash()).thenReturn("sha256:" + "b".repeat(64));
        when(concept.getCanonicalHash()).thenReturn("sha256:" + "c".repeat(64));
        when(concept.getCandidateJson()).thenReturn("""
            {"targetRegion":"대한민국","revenueModel":"월 구독","price":"월 9,900원","channels":"직접 영업","differentiators":"당일 도입",
             "preMarketSomShareHypothesis":{"targetSharePercent":2,"horizonYears":3},
             "preMarketSomHypothesis":{"amount":100000000,"currency":"KRW"},
             "valueSemantics":[
               {"fieldKey":"targetRegion","source":"USER_CONFIRMED","authority":"LOCKED"},
               {"fieldKey":"revenueModel","source":"AI_HYPOTHESIS","authority":"OPEN"},
               {"fieldKey":"price","source":"USER_INPUT","authority":"LOCKED"},
               {"fieldKey":"channels","source":"AI_HYPOTHESIS","authority":"OPEN"},
               {"fieldKey":"differentiators","source":"AI_HYPOTHESIS","authority":"OPEN"},
               {"fieldKey":"preMarketSomShareHypothesis","source":"AI_HYPOTHESIS","authority":"OPEN"},
               {"fieldKey":"preMarketSomHypothesis","source":"AI_HYPOTHESIS","authority":"OPEN"}]}
            """);
        when(assessments.findByConceptIdAndProjectIdAndDeletedAtIsNull("concept-1", 41L))
            .thenReturn(Optional.of(mock(ConceptLegalAssessment.class)));
        when(selections.findByProjectIdAndRequestHashAndCurrentSelectionTrueAndDeletedAtIsNull(eq(41L), anyString()))
            .thenReturn(Optional.empty());
        when(selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L)).thenReturn(Optional.empty());
        when(selections.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(decisions.save(any())).thenAnswer(invocation -> { ConceptHypothesisDecision value = invocation.getArgument(0); saved.add(value); return value; });
        when(decisions.findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(nullable(Long.class)))
            .thenAnswer(_ignored -> saved);

        var result = service.select(7L, 41L, new CreateSelectionRequest("concept-1", "실행 범위가 명확함"));

        assertThat(result.hypotheses()).hasSize(7);
        assertThat(result.decisionComplete()).isFalse();
        assertThat(result.hypotheses()).filteredOn(value -> value.hypothesisType().equals("PRICE"))
            .singleElement().satisfies(value -> { assertThat(value.locked()).isTrue(); assertThat(value.finalValue()).isNotNull(); });
        assertThat(result.hypotheses()).filteredOn(value -> value.hypothesisType().equals("TARGET_REGION"))
            .singleElement().satisfies(value -> { assertThat(value.locked()).isTrue(); assertThat(value.finalValue()).isNotNull(); });
        verify(decisions, times(7)).save(any());
    }

    @Test
    void editedRevenueQueuesDeltaLegalWithoutAcceptingBeforeProviderResult() {
        Harness h = new Harness(HypothesisType.REVENUE_MODEL, "\"월 구독\"");
        h.candidate("{\"revenueModel\":\"월 구독\",\"valueSemantics\":[]}");
        h.deltaContext();
        var result = h.service.decide(7L, 41L, "REVENUE_MODEL",
            new HypothesisActionRequest(HypothesisAction.EDIT_AND_ACCEPT, 1, h.mapper.valueToTree("거래 수수료")),
            "command-delta-1", "request-delta-1");

        assertThat(result.hypothesis().decisionStatus()).isEqualTo("PROPOSED");
        assertThat(result.hypothesis().finalValue()).isNull();
        assertThat(result.status()).isEqualTo("QUEUED");
        assertThat(result.taskRunId()).isEqualTo("task-1");
        verify(h.taskRuns).createWithDisposition(eq(7L), eq(41L),
            eq(TaskType.CONCEPT_DELTA_LEGAL_REVIEW), anyString(), anyString(), anyString(),
            anyString(), eq("command-delta-1"), eq("request-delta-1"), eq(1));
    }

    @Test
    void editedSomIsAcceptedWithoutDeltaLegalCall() {
        Harness h = new Harness(HypothesisType.PRE_MARKET_SOM, "{\"amount\":100000000,\"currency\":\"KRW\"}");
        h.candidate("{\"preMarketSomHypothesis\":{\"amount\":100000000,\"currency\":\"KRW\"}}");

        var result = h.service.decide(7L, 41L, "PRE_MARKET_SOM",
            new HypothesisActionRequest(HypothesisAction.EDIT_AND_ACCEPT, 1,
                h.mapper.readTree("{\"amount\":200000000,\"currency\":\"KRW\"}")),
            "command-som-1", "request-som-1");

        assertThat(result.hypothesis().decisionStatus()).isEqualTo("USER_EDITED_ACCEPTED");
        assertThat(result.hypothesis().legalReviewStatus()).isEqualTo("NOT_REQUIRED");
        verify(h.taskRuns, never()).createWithDisposition(anyLong(), anyLong(), any(), anyString(),
            anyString(), anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void alternativeQueuesWithoutRejectingCurrentProposal() {
        Harness h = new Harness(HypothesisType.CHANNELS, "\"직접 영업\"");
        h.candidate("{\"channels\":\"직접 영업\"}");
        var result = h.service.decide(7L, 41L, "CHANNELS",
            new HypothesisActionRequest(HypothesisAction.REQUEST_ALTERNATIVE, 1, null),
            "command-alternative-1", "request-alternative-1");

        assertThat(result.hypothesis().decisionStatus()).isEqualTo("PROPOSED");
        assertThat(result.hypothesis().proposalVersion()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("QUEUED");
        assertThat(h.selection.getActiveActionTaskRunId()).isEqualTo("task-1");
    }

    @Test
    void unsupportedTargetRegionEditIsBlockedBeforeDeltaLegalProviderCall() {
        Harness h = new Harness(HypothesisType.TARGET_REGION, "\"대한민국\"");
        h.candidate("{\"targetRegion\":\"대한민국\",\"valueSemantics\":[]}");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> h.service.decide(7L, 41L, "TARGET_REGION",
            new HypothesisActionRequest(HypothesisAction.EDIT_AND_ACCEPT, 1,
                h.mapper.valueToTree("미국 캘리포니아")), "command-region-1", "request-region-1"))
            .isInstanceOf(com.aivle.backend.common.exception.BusinessException.class)
            .hasMessageContaining("대한민국");
        verify(h.taskRuns, never()).createWithDisposition(anyLong(), anyLong(), any(), anyString(),
            anyString(), anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    private static final class Harness {
        final ObjectMapper mapper = new ObjectMapper();
        final ProjectRepository projects = mock(ProjectRepository.class);
        final ConceptFactoryRunRepository runs = mock(ConceptFactoryRunRepository.class);
        final ConceptRepository concepts = mock(ConceptRepository.class);
        final ConceptLegalAssessmentRepository assessments = mock(ConceptLegalAssessmentRepository.class);
        final ConceptSelectionRepository selections = mock(ConceptSelectionRepository.class);
        final ConceptHypothesisDecisionRepository decisions = mock(ConceptHypothesisDecisionRepository.class);
        final ConceptLegalFactPatternMapper patterns = mock(ConceptLegalFactPatternMapper.class);
        final TaskRunService taskRuns = mock(TaskRunService.class);
        final CanonicalInputHasher hasher = mock(CanonicalInputHasher.class);
        final JobEventPublisher events = mock(JobEventPublisher.class);
        final ConceptSelectionService service = new ConceptSelectionService(
            projects, runs, concepts, assessments, selections, decisions, patterns, mapper,
            new LegalJurisdictionResolver(), taskRuns, hasher, events);
        final ConceptSelection selection = ConceptSelection.select(41L, "concept-1", "선택 이유",
            "sha256:" + "a".repeat(64), 7L, Instant.parse("2026-08-08T00:00:00Z"));
        final Concept concept = mock(Concept.class);

        Harness(HypothesisType type, String proposedJson) {
            ReflectionTestUtils.setField(selection, "id", 99L);
            when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(41L, 7L)).thenReturn(Optional.of(mock(Project.class)));
            when(selections.findByProjectIdAndCurrentSelectionTrueAndDeletedAtIsNull(41L)).thenReturn(Optional.of(selection));
            when(selections.findByIdAndProjectIdAndDeletedAtIsNull(99L, 41L)).thenReturn(Optional.of(selection));
            ConceptHypothesisDecision current = ConceptHypothesisDecision.initial(
                selection, type, proposedJson, "AI_HYPOTHESIS", false, 7L, Instant.now());
            when(decisions.findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(
                nullable(Long.class), eq(type))).thenReturn(Optional.of(current));
            when(decisions.findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(nullable(Long.class)))
                .thenReturn(List.of(current));
            when(decisions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(concepts.findByIdAndProjectIdAndPublishedTrueAndDeletedAtIsNull("concept-1", 41L))
                .thenReturn(Optional.of(concept));
            when(concept.getId()).thenReturn("concept-1");
            when(concept.getProjectId()).thenReturn(41L);
            when(concept.getCanonicalHash()).thenReturn("sha256:" + "c".repeat(64));
            when(hasher.hash(any(), eq("1.0"), eq("ko-KR"), anyString()))
                .thenReturn("sha256:" + "e".repeat(64));
            TaskRun task = mock(TaskRun.class);
            when(task.getId()).thenReturn("task-1");
            when(task.getState()).thenReturn(TaskRunState.QUEUED);
            when(taskRuns.createWithDisposition(anyLong(), anyLong(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new TaskRunService.CreateResult(task, true, false));
        }

        void candidate(String json) { when(concept.getCandidateJson()).thenReturn(json); }

        void deltaContext() {
            var pattern = mapper.createObjectNode().put("schemaVersion", "2.0");
            when(patterns.map(any())).thenReturn(new ConceptLegalFactPatternMapper.Result(
                pattern, "sha256:" + "d".repeat(64)));
            ConceptLegalAssessment assessment = mock(ConceptLegalAssessment.class);
            LegalContextPack pack = mock(LegalContextPack.class);
            when(assessment.getContextPack()).thenReturn(pack);
            when(pack.getSourceSnapshotHash()).thenReturn("sha256:" + "a".repeat(64));
            when(pack.getRegistryVersion()).thenReturn("legal-registry-v1");
            when(pack.getCanonicalContextJson()).thenReturn("[]");
            when(assessments.findByConceptIdAndProjectIdAndDeletedAtIsNull("concept-1", 41L))
                .thenReturn(Optional.of(assessment));
        }
    }
}
