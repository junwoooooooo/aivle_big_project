package com.aivle.backend.pipeline.conceptportfolio;

import static com.aivle.backend.pipeline.conceptportfolio.selection.api.ConceptPortfolioSelectionApiModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.pipeline.conceptportfolio.application.*;
import com.aivle.backend.pipeline.conceptportfolio.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.repository.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.*;
import com.aivle.backend.pipeline.idea.domain.*;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.user.entity.User;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ConceptPortfolioSelectionServiceP5Tests {
    private static final String HASH = "sha256:" + "a".repeat(64);
    private final ProjectRepository projects=mock(ProjectRepository.class);
    private final ConceptPortfolioRunRepository runs=mock(ConceptPortfolioRunRepository.class);
    private final ConceptPortfolioConceptRepository concepts=mock(ConceptPortfolioConceptRepository.class);
    private final ConceptPortfolioSelectionRepository selections=mock(ConceptPortfolioSelectionRepository.class);
    private final ConceptPortfolioHypothesisDecisionRepository hypotheses=mock(ConceptPortfolioHypothesisDecisionRepository.class);
    private final ConceptPortfolioDeltaLegalReviewRepository deltas=mock(ConceptPortfolioDeltaLegalReviewRepository.class);
    private final ConceptLegalRegulatoryReportRepository reports=mock(ConceptLegalRegulatoryReportRepository.class);
    private final MarketAnalysisSeedSnapshotRepository marketSeeds=mock(MarketAnalysisSeedSnapshotRepository.class);
    private final IdeaBriefFieldRepository briefFields=mock(IdeaBriefFieldRepository.class);
    private final ConceptPortfolioSeedBuilder seedBuilder=mock(ConceptPortfolioSeedBuilder.class);
    private final ConceptPortfolioSelectionTaskFactory taskFactory=mock(ConceptPortfolioSelectionTaskFactory.class);
    private final TaskRunService taskRuns=mock(TaskRunService.class);
    private final ConceptPortfolioJsonHasher hasher=mock(ConceptPortfolioJsonHasher.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private final Clock clock=Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"),ZoneOffset.UTC);
    private final ConceptPortfolioSelectionService service=new ConceptPortfolioSelectionService(projects,runs,concepts,
        selections,hypotheses,deltas,reports,marketSeeds,briefFields,seedBuilder,taskFactory,taskRuns,hasher,mapper,clock);

    @BeforeEach
    void defaults(){reset(projects,runs,concepts,selections,hypotheses,deltas,reports,marketSeeds,briefFields,seedBuilder,taskFactory,taskRuns,hasher);
        when(hasher.hash(any())).thenReturn(HASH); when(selections.findByProjectIdAndIdempotencyKeyAndDeletedAtIsNull(anyLong(),anyString())).thenReturn(Optional.empty());
        when(selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(anyLong())).thenReturn(Optional.empty());
        when(hypotheses.findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(anyLong())).thenReturn(List.of());
        when(reports.findBySelectionIdAndStatusAndDeletedAtIsNull(anyLong(),anyString())).thenReturn(Optional.empty());
        when(marketSeeds.findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(anyLong())).thenReturn(Optional.empty());
        ObjectNode seed=mapper.createObjectNode();seed.putObject("seed").put("ideaOverview","seed");
        when(seedBuilder.build(any(),anyList(),anyInt())).thenReturn(new ConceptPortfolioSeedBuilder.BuiltInput(seed,seed.toString()));
        TaskRun task=mock(TaskRun.class);when(task.getId()).thenReturn("prepare-task");
        when(taskFactory.create(anyLong(),any(),anyString(),any(),anyString(),isNull())).thenAnswer(invocation->{
            ConceptPortfolioSelection selection=invocation.getArgument(1);String action=invocation.getArgument(2);
            selection.attachTask("prepare-task",action);return task;});
        when(selections.saveAndFlush(any())).thenAnswer(invocation->{ConceptPortfolioSelection value=invocation.getArgument(0);ReflectionTestUtils.setField(value,"id",17L);return value;});}

    @ParameterizedTest
    @ValueSource(ints={1,2,3,4,5})
    void selectsAnyAcceptedPortfolioSizeWithoutCompareGate(int produced){Fixture f=fixture(ConceptPortfolioRunStatus.RESULTS_AVAILABLE,produced,true);
        SelectionView result=service.select(7L,42L,new CreateSelectionRequest(f.run.getId(),f.concept.getId(),"명시적 사용자 선택","key-"+produced));
        assertThat(result.selectionId()).isEqualTo(17L);assertThat(result.conceptId()).isEqualTo(f.concept.getId());
        assertThat(result.status()).isEqualTo("HYPOTHESES_PREPARING");assertThat(result.activeTaskRunId()).isEqualTo("prepare-task");}

    @Test
    void selectsAcceptedConceptWhileAnotherCandidateInputIsOpen(){Fixture f=fixture(ConceptPortfolioRunStatus.RESULTS_WITH_OPEN_INPUT,2,true);
        SelectionView result=service.select(7L,42L,new CreateSelectionRequest(f.run.getId(),f.concept.getId(),"입력 대기와 독립","open-input-key"));
        assertThat(result.conceptId()).isEqualTo(f.concept.getId());}

    @Test
    void rejectsStaleRunAndNonselectableConcept(){Fixture stale=fixture(ConceptPortfolioRunStatus.STALE,1,true);
        assertThatThrownBy(()->service.select(7L,42L,new CreateSelectionRequest(stale.run.getId(),stale.concept.getId(),"stale","stale-key"))).isInstanceOf(BusinessException.class);
        Fixture invalid=fixture(ConceptPortfolioRunStatus.RESULTS_AVAILABLE,1,false);
        assertThatThrownBy(()->service.select(7L,42L,new CreateSelectionRequest(invalid.run.getId(),invalid.concept.getId(),"invalid","invalid-key"))).isInstanceOf(BusinessException.class);}

    @Test
    void enforcesOwnershipBeforeReadingPortfolio(){when(projects.findByIdForUpdate(42L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.select(7L,42L,new CreateSelectionRequest("run","concept","reason","key"))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(runs,concepts);}

    @Test
    void noCurrentSelectionUsesResourceNotFoundInsteadOfAValidationError(){
        Fixture f=fixture(ConceptPortfolioRunStatus.RESULTS_AVAILABLE,1,true);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(42L,7L)).thenReturn(Optional.of(f.run.getProject()));
        assertThatThrownBy(()->service.current(7L,42L))
            .isInstanceOfSatisfying(BusinessException.class,
                failure->assertThat(failure.getErrorCode()).isEqualTo(com.aivle.backend.common.exception.ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void replaysSameSelectionKeyAndRejectsDifferentPayload(){Fixture f=fixture(ConceptPortfolioRunStatus.RESULTS_AVAILABLE,1,true);
        ConceptPortfolioSelection existing=portfolioSelection(f,"same-key",HASH);ReflectionTestUtils.setField(existing,"id",99L);
        when(selections.findByProjectIdAndIdempotencyKeyAndDeletedAtIsNull(42L,"same-key")).thenReturn(Optional.of(existing));
        SelectionView replay=service.select(7L,42L,new CreateSelectionRequest(f.run.getId(),f.concept.getId(),"reason","same-key"));
        assertThat(replay.selectionId()).isEqualTo(99L);verify(selections,never()).saveAndFlush(any());
        ConceptPortfolioSelection conflict=portfolioSelection(f,"conflict-key","sha256:"+"b".repeat(64));
        when(selections.findByProjectIdAndIdempotencyKeyAndDeletedAtIsNull(42L,"conflict-key")).thenReturn(Optional.of(conflict));
        assertThatThrownBy(()->service.select(7L,42L,new CreateSelectionRequest(f.run.getId(),f.concept.getId(),"reason","conflict-key"))).isInstanceOf(BusinessException.class);}

    @Test
    void explicitChangeStalesPreviousSelectionButRecoveredConceptNeverAutoChangesIt(){Fixture f=fixture(ConceptPortfolioRunStatus.RESULTS_AVAILABLE,2,true);
        ConceptPortfolioSelection previous=portfolioSelection(f,"old-key",HASH);ReflectionTestUtils.setField(previous,"id",3L);
        when(selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(42L)).thenReturn(Optional.of(previous));
        service.select(7L,42L,new CreateSelectionRequest(f.run.getId(),f.concept.getId(),"새 명시 선택","new-key"));
        assertThat(previous.isCurrent()).isFalse();assertThat(previous.getStatus()).isEqualTo(ConceptPortfolioSelectionStatus.STALE);
        verify(selections).flush();}

    @Test
    void buildHandoffUsesOnlyLatestApprovedDeltaForCurrentHypothesisRevision(){Fixture f=fixture(ConceptPortfolioRunStatus.RESULTS_AVAILABLE,1,true);
        ConceptPortfolioSelection selection=portfolioSelection(f,"selection",HASH);ReflectionTestUtils.setField(selection,"id",17L);
        ReflectionTestUtils.setField(selection,"status",ConceptPortfolioSelectionStatus.LEGAL_REPORT_READY);
        ReflectionTestUtils.setField(selection,"hypothesisRevision",3);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(42L,7L)).thenReturn(Optional.of(f.run.getProject()));
        when(selections.findLocked(17L)).thenReturn(Optional.of(selection));when(runs.findLocked(f.run.getId())).thenReturn(Optional.of(f.run));
        when(concepts.findByIdAndProjectIdAndDeletedAtIsNull(f.concept.getId(),42L)).thenReturn(Optional.of(f.concept));
        when(hypotheses.findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(17L)).thenReturn(readyHypotheses(selection));
        ConceptLegalRegulatoryReport report=ConceptLegalRegulatoryReport.create("report",selection,HASH,null,HASH,"{}",HASH,7L,LocalDate.of(2026,8,11));
        when(reports.findBySelectionIdAndStatusAndDeletedAtIsNull(17L,"CURRENT")).thenReturn(Optional.of(report));
        String deltaJson="{\"deltaLegalResult\":{\"reviewToken\":\"sha256:"+"3".repeat(64)+"\",\"candidateId\":\"candidate\",\"hypothesisTypes\":[\"PRICE\"],\"status\":\"PASSED\",\"approved\":true,\"legalReview\":{\"candidateId\":\"candidate\",\"route\":\"ACCEPT\",\"sourceStatus\":\"OFFICIAL_EVIDENCE\",\"safeSummary\":\"ok\"}}}";
        ConceptPortfolioDeltaLegalReview effective=ConceptPortfolioDeltaLegalReview.create(selection,"delta-3",3,HASH,"[\"PRICE\"]","PASSED",true,deltaJson,HASH);
        when(deltas.findFirstBySelectionIdAndHypothesisRevisionAndApprovedTrueAndDeletedAtIsNullOrderByCreatedAtDesc(17L,3)).thenReturn(Optional.of(effective));
        List<ConceptPortfolioDeltaLegalReview> sixHistorical = IntStream.range(0,6)
            .mapToObj(index -> ConceptPortfolioDeltaLegalReview.create(selection,"history-"+index,2,HASH,
                "[\"PRICE\"]","PASSED",true,deltaJson,HASH)).toList();
        when(deltas.findAllBySelectionIdAndDeletedAtIsNullOrderByCreatedAtAsc(17L)).thenReturn(sixHistorical);
        ArgumentCaptor<tools.jackson.databind.JsonNode> input=ArgumentCaptor.forClass(tools.jackson.databind.JsonNode.class);
        service.finalizeMarketSeed(7L,42L,17L,new ActionRequest("market-key"));
        verify(taskFactory).create(eq(7L),eq(selection),eq("BUILD_HANDOFF"),input.capture(),eq("market-key"),isNull());
        assertThat(input.getValue().path("approvedDeltaLegalResults")).hasSize(1);
        assertThat(input.getValue().path("approvedDeltaLegalResults").get(0).path("reviewToken").asText()).endsWith("3".repeat(64));
        verify(deltas,never()).findAllBySelectionIdAndDeletedAtIsNullOrderByCreatedAtAsc(17L);}

    @Test
    void activeActionAlwaysExposesWaitAndRejectsConcurrentDifferentAction(){Fixture f=fixture(ConceptPortfolioRunStatus.RESULTS_AVAILABLE,1,true);
        ConceptPortfolioSelection selection=portfolioSelection(f,"selection",HASH);ReflectionTestUtils.setField(selection,"id",17L);
        ReflectionTestUtils.setField(selection,"status",ConceptPortfolioSelectionStatus.READY_FOR_MARKET);
        selection.attachTask("active-task","PROPOSE_ALTERNATIVE");
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(42L,7L)).thenReturn(Optional.of(f.run.getProject()));
        when(selections.findByIdAndProjectIdAndDeletedAtIsNull(17L,42L)).thenReturn(Optional.of(selection));
        when(selections.findLocked(17L)).thenReturn(Optional.of(selection));
        TaskRun active=mock(TaskRun.class);when(active.getId()).thenReturn("active-task");when(active.getIdempotencyKey()).thenReturn("alternative-key");
        when(taskRuns.getOwned(7L,42L,"active-task")).thenReturn(active);
        assertThat(service.get(7L,42L,17L).nextAction()).isEqualTo("WAIT");
        assertThatThrownBy(()->service.confirm(7L,42L,17L,new ConfirmHypothesesRequest(mapper.createObjectNode(),true,"confirm-key")))
            .isInstanceOf(BusinessException.class);}

    @Test
    void finalReportPreservesPhysicalActivityAndPrivacyOnlyFromTheSelectedCandidateSnapshot(){
        Fixture f=fixture(ConceptPortfolioRunStatus.RESULTS_AVAILABLE,1,true);
        String candidateJson="{\"candidateId\":\"candidate\",\"candidate\":{\"physicalActivities\":[\"방문 설치 없음\"],\"personalDataUsage\":[\"예약 연락처만 사용\"],\"transactionFlow\":[\"고객→플랫폼\"],\"paymentFlow\":[\"고객→판매자\"]}}";
        ConceptPortfolioConcept selected=ConceptPortfolioConcept.create(f.run,1,"candidate","lineage","plan",null,
            "사업안","summary","IMPLEMENTABLE",candidateJson,"{\"candidateId\":\"candidate\",\"route\":\"ACCEPT\"}",HASH);
        ConceptPortfolioSelection selection=ConceptPortfolioSelection.create(42L,f.run.getId(),selected.getId(),
            selected.getCandidateId(),HASH,HASH,"reason",HASH,"selection",7L,clock.instant());
        ReflectionTestUtils.setField(selection,"id",17L);
        ReflectionTestUtils.setField(selection,"status",ConceptPortfolioSelectionStatus.READY_FOR_LEGAL_REPORT);
        when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(42L,7L)).thenReturn(Optional.of(f.run.getProject()));
        when(selections.findLocked(17L)).thenReturn(Optional.of(selection));
        when(runs.findLocked(f.run.getId())).thenReturn(Optional.of(f.run));
        when(concepts.findByIdAndProjectIdAndDeletedAtIsNull(selected.getId(),42L)).thenReturn(Optional.of(selected));
        when(hypotheses.findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(17L))
            .thenReturn(readyHypotheses(selection));
        when(deltas.findAllBySelectionIdAndDeletedAtIsNullOrderByCreatedAtAsc(17L)).thenReturn(List.of());
        when(reports.save(any())).thenAnswer(invocation->{ConceptLegalRegulatoryReport report=invocation.getArgument(0);
            ReflectionTestUtils.setField(report,"createdAt",LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));return report;});

        LegalReportView view=service.finalizeReport(7L,42L,17L);
        assertThat(view.report().path("physicalActivities")).hasSize(1);
        assertThat(view.report().path("physicalActivities").get(0).asText()).isEqualTo("방문 설치 없음");
        assertThat(view.report().path("personalDataUsage").get(0).asText()).isEqualTo("예약 연락처만 사용");
        assertThat(view.generatedAt()).isEqualTo(LocalDateTime.of(2026,8,11,0,0));
        assertThat(view.report().toString()).doesNotContain("배송", "다른 후보");
    }

    private Fixture fixture(ConceptPortfolioRunStatus status,int produced,boolean selectable){User user=User.create("owner@example.com","hash","owner");ReflectionTestUtils.setField(user,"id",7L);
        Project project=Project.create(user,"project","desc","industry");ReflectionTestUtils.setField(project,"id",42L);
        IdeaBrief brief=IdeaBrief.initial(project,7L);ReflectionTestUtils.setField(brief,"status",IdeaBriefStatus.CONFIRMED);ReflectionTestUtils.setField(brief,"snapshotHash",HASH);
        ConceptPortfolioRun run=ConceptPortfolioRun.queued(project,brief,5,HASH,"run-key",7L);ReflectionTestUtils.setField(run,"id","run-"+produced+"-"+status);ReflectionTestUtils.setField(run,"productStatus",status);ReflectionTestUtils.setField(run,"producedConceptCount",produced);
        ConceptPortfolioConcept concept=ConceptPortfolioConcept.create(run,1,"candidate","lineage","plan",null,"사업안","summary","IMPLEMENTABLE","{\"candidateId\":\"candidate\",\"candidate\":{}}","{\"candidateId\":\"candidate\",\"route\":\"ACCEPT\"}",HASH);
        ReflectionTestUtils.setField(concept,"selectable",selectable);when(projects.findByIdForUpdate(42L)).thenReturn(Optional.of(project));when(runs.findOwned(7L,42L,run.getId())).thenReturn(Optional.of(run));when(concepts.findByIdAndProjectIdAndDeletedAtIsNull(concept.getId(),42L)).thenReturn(Optional.of(concept));
        return new Fixture(run,concept);}
    private ConceptPortfolioSelection portfolioSelection(Fixture f,String key,String requestHash){return ConceptPortfolioSelection.create(42L,f.run.getId(),f.concept.getId(),f.concept.getCandidateId(),HASH,HASH,"reason",requestHash,key,7L,Instant.now(clock));}
    private List<ConceptPortfolioHypothesisDecision> readyHypotheses(ConceptPortfolioSelection selection){return Arrays.stream(PortfolioHypothesisType.values()).map(type->ConceptPortfolioHypothesisDecision.create(selection.getId(),42L,selection.getConceptId(),type,"\"proposed\"","\"final\"","USER_INPUT","USER_EDITED_ACCEPTED",1,false,"VALID","valid","NONE","PASSED",false,7L,Instant.now(clock))).toList();}
    private record Fixture(ConceptPortfolioRun run,ConceptPortfolioConcept concept){}
}
