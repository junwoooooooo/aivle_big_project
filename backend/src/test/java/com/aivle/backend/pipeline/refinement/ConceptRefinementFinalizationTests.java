package com.aivle.backend.pipeline.refinement;

import static org.assertj.core.api.Assertions.*;import static org.mockito.ArgumentMatchers.*;import static org.mockito.Mockito.*;
import com.aivle.backend.common.exception.*;import com.aivle.backend.pipeline.businessvalidation.BusinessValidationCoordinator.CompletedSource;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.*;import com.aivle.backend.pipeline.conceptportfolio.selection.repository.*;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;import com.aivle.backend.taskrun.domain.*;import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.*;import com.aivle.backend.user.entity.User;
import java.time.*;import java.util.*;import org.junit.jupiter.api.*;import org.junit.jupiter.api.extension.ExtendWith;import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;import org.springframework.test.util.ReflectionTestUtils;import tools.jackson.databind.*;import tools.jackson.databind.node.*;

@ExtendWith(MockitoExtension.class)
class ConceptRefinementFinalizationTests {
 static final String HASH="sha256:"+"a".repeat(64);static final long PROJECT=41,OWNER=7;
 @Mock ProjectRepository projects;@Mock ConceptRefinementRoundRepository rounds;@Mock ConceptRefinementFinalRepository finals;
 @Mock ConceptRefinementLineageGuard lineage;@Mock MarketAnalysisSeedSnapshotRepository seeds;@Mock BmPlanPreparationService bmPlans;
 @Mock ConceptPortfolioSelectionService selectionService;@Mock Project project;@Mock User owner;@Mock TaskRun task;
 final ObjectMapper mapper=new ObjectMapper();final Clock clock=Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"),ZoneOffset.UTC);
 ConceptRefinementDecisionContract contract;ConceptRefinementFinalizationService service;CompletedSource source;MarketAnalysisSeedSnapshot seed;
 @BeforeEach void setup(){contract=new ConceptRefinementDecisionContract(mapper,new ConceptPortfolioJsonHasher(mapper));
  service=new ConceptRefinementFinalizationService(projects,rounds,finals,contract,lineage,seeds,bmPlans,selectionService,new ConceptPortfolioJsonHasher(mapper),mapper,clock);
  source=new CompletedSource("session",91L,92L,"seed-1",31L,4,3,HASH);seed=MarketAnalysisSeedSnapshot.createPortfolio("seed-1",PROJECT,31L,"concept","legal-1","2.0",HASH,HASH,
   "{\"selectedConcept\":{\"identity\":{\"targetUsers\":\"users\"},\"solution\":{\"featureSet\":[\"a\"]}},\"finalHypotheses\":{}}",OWNER,clock.instant());
  lenient().when(project.getOwner()).thenReturn(owner);lenient().when(owner.getId()).thenReturn(OWNER);lenient().when(projects.findByIdForUpdate(PROJECT)).thenReturn(Optional.of(project));
  lenient().when(projects.findByIdAndOwnerIdAndDeletedAtIsNull(PROJECT,OWNER)).thenReturn(Optional.of(project));lenient().when(lineage.proposalBaselineCurrent(eq(OWNER),eq(PROJECT),any())).thenReturn(true);
  lenient().when(lineage.postApplyCurrent(eq(PROJECT),any())).thenReturn(true);lenient().when(seeds.findByIdAndStaleAtIsNullAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(seed));
  lenient().when(bmPlans.current(PROJECT)).thenReturn(new BmPlanPreparationService.PlanView(mapper.createObjectNode().put("customer_relationship","D"),mapper.createObjectNode(),3));
  lenient().when(finals.save(any())).thenAnswer(i->{ConceptRefinementFinal f=i.getArgument(0);ReflectionTestUtils.setField(f,"id",501L);return f;});lenient().when(task.getId()).thenReturn("handoff-task");}

 @Test void keepCurrentAndNoChangesCreateDistinctSynchronousFinalsWithoutTask(){ConceptRefinementRound keep=keepRound();stub(keep);
  var keepView=service.finalizeRound(OWNER,PROJECT,"keep",1,keep.getDecisionHash());assertThat(keepView.outcome()).isEqualTo("KEEP_CURRENT");assertThat(keep.getFinalMarketSeedSnapshotId()).isEqualTo("seed-1");
  verify(selectionService,never()).finalizeMarketSeedFromRefinement(anyLong(),anyLong(),anyLong(),anyString(),any(),any(),anyString(),anyString());
  ConceptRefinementRound no=noChangesRound();stub(no);var noView=service.finalizeRound(OWNER,PROJECT,"no",1,null);assertThat(noView.outcome()).isEqualTo("NO_CHANGES");assertThat(no.getFinalMarketSeedSnapshotId()).isEqualTo("seed-1");}

 @Test void bmOnlyRefinedReusesSourceSeedAndAppliedBmRevision(){ConceptRefinementRound r=appliedRound(proposal("keyActivities",List.of("A"),List.of("B")),4,4);stub(r);
  when(bmPlans.current(PROJECT)).thenReturn(new BmPlanPreparationService.PlanView(mapper.createObjectNode(),mapper.createObjectNode(),4));
  var view=service.finalizeRound(OWNER,PROJECT,"bm",1,r.getDecisionHash());assertThat(view.outcome()).isEqualTo("REFINED");assertThat(r.getFinalMarketSeedSnapshotId()).isEqualTo("seed-1");
  ArgumentCaptor<ConceptRefinementFinal> saved=ArgumentCaptor.forClass(ConceptRefinementFinal.class);verify(finals).save(saved.capture());assertThat(saved.getValue().getFinalBmPlanRevision()).isEqualTo(4);}

 @Test void overlayQueuesOneAuxiliaryHandoffWithServerOverlay(){ConceptRefinementRound r=appliedRound(proposal("targetUsers","old","new"),4,3);stub(r);
  when(selectionService.currentReport(OWNER,PROJECT,31L)).thenReturn(null);when(selectionService.finalizeMarketSeedFromRefinement(eq(OWNER),eq(PROJECT),eq(31L),eq("seed-1"),any(),any(),eq("overlay"),anyString())).thenReturn(task);
  var view=service.finalizeRound(OWNER,PROJECT,"overlay",1,r.getDecisionHash());assertThat(view.state()).isEqualTo("FINALIZING");assertThat(r.getFinalizationTaskRunId()).isEqualTo("handoff-task");
  ArgumentCaptor<ObjectNode> overlay=ArgumentCaptor.forClass(ObjectNode.class);verify(selectionService).finalizeMarketSeedFromRefinement(eq(OWNER),eq(PROJECT),eq(31L),eq("seed-1"),overlay.capture(),any(),eq("overlay"),anyString());assertThat(overlay.getValue().path("targetUsers").asText()).isEqualTo("new");}

 @Test void hypothesisFinalizesExistingReportThenQueuesOneHandoff(){ConceptRefinementRound r=appliedRound(proposal("price","10","12"),5,3);stub(r);
  when(selectionService.currentReport(OWNER,PROJECT,31L)).thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));when(selectionService.finalizeReport(OWNER,PROJECT,31L)).thenReturn(null);
  when(selectionService.finalizeMarketSeedFromRefinement(anyLong(),anyLong(),anyLong(),anyString(),any(),any(),eq("hyp"),anyString())).thenReturn(task);
  service.finalizeRound(OWNER,PROJECT,"hyp",1,r.getDecisionHash());verify(selectionService).finalizeReport(OWNER,PROJECT,31L);verify(selectionService,times(1)).finalizeMarketSeedFromRefinement(anyLong(),anyLong(),anyLong(),anyString(),any(),any(),eq("hyp"),anyString());}

 @Test void sameKeyReplaysSynchronousFinalWithoutDuplicate(){ConceptRefinementRound r=keepRound();stub(r);service.finalizeRound(OWNER,PROJECT,"same",1,r.getDecisionHash());
  ConceptRefinementFinal saved=finals.findByRoundIdAndDeletedAtIsNull(r.getId()).orElse(null);when(finals.findByRoundIdAndDeletedAtIsNull(r.getId())).thenReturn(Optional.ofNullable(saved));service.finalizeRound(OWNER,PROJECT,"same",1,r.getDecisionHash());verify(finals,times(1)).save(any());}

 @Test void currentBindsLatestFinalToItsExactRoundInsteadOfLatestProjectRound(){ConceptRefinementRound roundA=appliedRound(proposal("targetUsers","old","new"),4,3);ConceptRefinementFinal value=finalValue(roundA,"seed-new");
  ConceptRefinementRound roundB=base();ReflectionTestUtils.setField(roundB,"id",202L);when(finals.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT)).thenReturn(Optional.of(value));when(rounds.findById(101L)).thenReturn(Optional.of(roundA));lenient().when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT)).thenReturn(Optional.of(roundB));
  var current=service.current(OWNER,PROJECT);assertThat(current.stale()).isFalse();assertThat(current.sourceBusinessValidationSessionId()).isEqualTo("session");verify(rounds).findById(101L);verify(rounds,never()).findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT);}

 @Test void exactFinalRoundBindingMismatchCannotAppearCurrent(){ConceptRefinementRound r=appliedRound(proposal("targetUsers","old","new"),4,3);ConceptRefinementFinal value=finalValue(r,"seed-new");ReflectionTestUtils.setField(r,"selectionId",999L);
  when(finals.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT)).thenReturn(Optional.of(value));when(rounds.findById(101L)).thenReturn(Optional.of(r));assertThat(service.current(OWNER,PROJECT).stale()).isTrue();verify(lineage,never()).postApplyCurrent(PROJECT,r);}

 @Test void finalizingStaleReflectsPostApplyLineage(){ConceptRefinementRound r=appliedRound(proposal("targetUsers","old","new"),4,3);r.startFinalization("finalize",HASH,"task",clock.instant());when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT)).thenReturn(Optional.of(r));
  var current=service.current(OWNER,PROJECT);assertThat(current.state()).isEqualTo("FINALIZING");assertThat(current.sourceBusinessValidationSessionId()).isEqualTo("session");assertThat(current.stale()).isFalse();assertThat(current.value()).isNull();when(lineage.postApplyCurrent(PROJECT,r)).thenReturn(false);assertThat(service.current(OWNER,PROJECT).stale()).isTrue();}

 @Test void finalizationFailedWithValidPostApplyLineageIsNotStale(){ConceptRefinementRound r=appliedRound(proposal("targetUsers","old","new"),4,3);r.startFinalization("finalize",HASH,"task",clock.instant());r.finalizationFailed("task","FAILED");when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT)).thenReturn(Optional.of(r));
  var current=service.current(OWNER,PROJECT);assertThat(current.state()).isEqualTo("FINALIZATION_FAILED");assertThat(current.stale()).isFalse();assertThat(current.value()).isNull();}

 @Test void noFinalAndNoRoundReturnsNotStartedWithoutStaleness(){var current=service.current(OWNER,PROJECT);assertThat(current.state()).isEqualTo("NOT_STARTED");assertThat(current.sourceBusinessValidationSessionId()).isNull();assertThat(current.stale()).isFalse();assertThat(current.value()).isNull();}

 @Test void finalizedCurrentUsesFinalSeedNotStaleSource(){ConceptRefinementRound r=appliedRound(proposal("targetUsers","old","new"),4,3);ConceptRefinementFinal value=finalValue(r,"seed-new");when(finals.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT)).thenReturn(Optional.of(value));when(rounds.findById(101L)).thenReturn(Optional.of(r));when(seeds.findByIdAndStaleAtIsNullAndDeletedAtIsNull("seed-new")).thenReturn(Optional.of(seed));
  assertThat(service.current(OWNER,PROJECT).stale()).isFalse();when(seeds.findByIdAndStaleAtIsNullAndDeletedAtIsNull("seed-new")).thenReturn(Optional.empty());assertThat(service.current(OWNER,PROJECT).stale()).isTrue();}

 @Test void taggedHandoffPersistsNewSeedAndFinalizesWithoutReplacingSelectionWithFailedState(){Tagged f=tagged("targetUsers","old","new");
  f.materializer.complete(f.claim,f.context,f.response,f.result,f.input);
  verify(f.seedRepo).save(any(MarketAnalysisSeedSnapshot.class));verify(f.finalizer).createFinal(eq(OWNER),eq(PROJECT),eq(f.round),eq(ConceptRefinementFinal.Outcome.REFINED),eq("seed-new"),eq(4),eq(3),any(),any());
  assertThat(f.round.getState()).isEqualTo(ConceptRefinementRound.State.FINALIZED);assertThat(f.selection.getStatus()).isEqualTo(ConceptPortfolioSelectionStatus.READY_FOR_MARKET);verify(f.taskRuns).adopt(anyString(),anyString(),anyString(),anyString(),eq(HASH),eq("1.0"));}

 @Test void taggedHandoffRejectsTargetUsersAndFeatureSetMismatchBeforeAnySeedOrFinal(){for(String field:List.of("targetUsers","featureSet")){Tagged f=tagged(field,"old","new");
   if("targetUsers".equals(field))((ObjectNode)f.result.at("/handoff/marketAnalysisSeedSnapshot/selectedConcept/identity")).put("targetUsers","tampered");
   else ((ArrayNode)f.result.at("/handoff/marketAnalysisSeedSnapshot/selectedConcept/solution/featureSet")).removeAll().add("tampered");
   refreshHash(f);assertThatThrownBy(()->f.materializer.complete(f.claim,f.context,f.response,f.result,f.input)).isInstanceOf(ConceptPortfolioSelectionMaterializationService.ContractViolation.class);
   verify(f.seedRepo,never()).save(any());verify(f.finalizer,never()).createFinal(anyLong(),anyLong(),any(),any(),anyString(),anyInt(),anyInt(),any(),any());}}

 @Test void taggedHandoffRejectsHypothesisMismatchBeforePersistence(){Tagged f=tagged("targetUsers","old","new");
  ((ObjectNode)f.result.at("/handoff/marketAnalysisSeedSnapshot/finalHypotheses/price")).put("value","tampered");refreshHash(f);
  assertThatThrownBy(()->f.materializer.complete(f.claim,f.context,f.response,f.result,f.input)).isInstanceOf(ConceptPortfolioSelectionMaterializationService.ContractViolation.class);
  verify(f.seedRepo,never()).save(any());verify(f.finalizer,never()).createFinal(anyLong(),anyLong(),any(),any(),anyString(),anyInt(),anyInt(),any(),any());}

 @Test void taggedFailurePreservesSelectionStatusAndExplicitRetryStopsAtThreeAttempts(){Tagged f=tagged("targetUsers","old","new");
  f.materializer.fail(f.claim,f.context,"HANDOFF_FAILED","reason",false,f.input);assertThat(f.round.getState()).isEqualTo(ConceptRefinementRound.State.FINALIZATION_FAILED);
  assertThat(f.selection.getStatus()).isEqualTo(ConceptPortfolioSelectionStatus.READY_FOR_MARKET);verify(f.finalizer,never()).createFinal(anyLong(),anyLong(),any(),any(),anyString(),anyInt(),anyInt(),any(),any());
  stub(f.round);when(selectionService.finalizeMarketSeedFromRefinement(anyLong(),anyLong(),anyLong(),anyString(),any(),any(),anyString(),anyString())).thenReturn(task);
  service.finalizeRound(OWNER,PROJECT,"retry-2",1,f.round.getDecisionHash());assertThat(f.round.getFinalizationAttempt()).isEqualTo(2);f.round.finalizationFailed("handoff-task","FAILED");
  service.finalizeRound(OWNER,PROJECT,"retry-3",1,f.round.getDecisionHash());assertThat(f.round.getFinalizationAttempt()).isEqualTo(3);f.round.finalizationFailed("handoff-task","FAILED");
  assertThatThrownBy(()->service.finalizeRound(OWNER,PROJECT,"retry-4",1,f.round.getDecisionHash())).isInstanceOf(BusinessException.class);}

 @Test void recoveredFinalizationKeepsCurrentOutcomeAndAlwaysRequiresNewSeed(){ConceptRefinementRound r=appliedRound(proposal("price","10","12"),6,5);
  ReflectionTestUtils.setField(r,"state",ConceptRefinementRound.State.RECOVERED);ReflectionTestUtils.setField(r,"recoveredAt",clock.instant());
  ReflectionTestUtils.setField(r,"seedRebuildRequired",true);when(rounds.findAllByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberAscIdAsc(PROJECT,"session"))
   .thenReturn(List.of(r));var outcome=service.outcome(r);assertThat(outcome.outcome()).isEqualTo(ConceptRefinementFinal.Outcome.KEEP_CURRENT);
  assertThat(outcome.newSeed()).isTrue();assertThat(outcome.selectedChanges()).isEmpty();}

 @Test void recoveredFinalizationRetainsPriorAppliedHistoryButExcludesBlockedCurrentSelection(){ConceptRefinementRound prior=appliedRound(proposal("targetUsers","old","kept"),5,3);prior.continued();
  ConceptRefinementRound current=ConceptRefinementRound.next(prior,5,3,"{\"targetUsers\":\"kept\"}",true,"proposal-2","next",HASH);ReflectionTestUtils.setField(current,"id",202L);
  ObjectNode blocked=proposal("price","10","12");current.materialize("["+blocked+"]","[]",true);var set=contract.proposalSet(current);var decision=contract.decision(current,set,set.orderedKeys(),false);
  current.recordDecision(decision.snapshot().toString(),decision.hash(),"d2",OWNER,clock.instant(),false);ReflectionTestUtils.setField(current,"state",ConceptRefinementRound.State.RECOVERED);
  ReflectionTestUtils.setField(current,"recoveredAt",clock.instant());ReflectionTestUtils.setField(current,"appliedSelectionRevision",6);ReflectionTestUtils.setField(current,"appliedBmPlanRevision",3);
  when(rounds.findAllByProjectIdAndBusinessValidationSessionIdAndDeletedAtIsNullOrderByRoundNumberAscIdAsc(PROJECT,"session")).thenReturn(List.of(prior,current));
  var outcome=service.outcome(current);assertThat(outcome.outcome()).isEqualTo(ConceptRefinementFinal.Outcome.REFINED);
  assertThat(outcome.selectedChanges()).hasSize(1);assertThat(outcome.selectedChanges().get(0).path("fieldKey").asText()).isEqualTo("targetUsers");
  assertThat(outcome.overlayNode().path("targetUsers").asText()).isEqualTo("kept");}

 void stub(ConceptRefinementRound r){when(rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(PROJECT)).thenReturn(Optional.of(r));}
 ConceptRefinementRound base(){ConceptRefinementRound r=ConceptRefinementRound.start(PROJECT,source,"proposal","start",HASH);ReflectionTestUtils.setField(r,"id",101L);return r;}
 ConceptRefinementRound keepRound(){ConceptRefinementRound r=base();r.materialize("["+proposal("price","10","12")+"]","[]",true);var set=contract.proposalSet(r);var d=contract.decision(r,set,List.of(),true);r.recordDecision(d.snapshot().toString(),d.hash(),"d",OWNER,clock.instant(),true);return r;}
 ConceptRefinementRound noChangesRound(){ConceptRefinementRound r=base();r.materialize("[]","[]",false);return r;}
 ConceptRefinementRound appliedRound(ObjectNode p,int sr,int br){ConceptRefinementRound r=base();r.materialize("["+p+"]","[]",true);var set=contract.proposalSet(r);var d=contract.decision(r,set,set.orderedKeys(),false);r.recordDecision(d.snapshot().toString(),d.hash(),"d",OWNER,clock.instant(),false);r.startLocalApplication("a",contract.applicationHash(r),clock.instant());r.recordAppliedLineage(sr,br,clock.instant());r.readyForFinalization();return r;}
 ConceptRefinementFinal finalValue(ConceptRefinementRound r,String seedId){r.finalized(501L,seedId,clock.instant());ConceptRefinementFinal value=ConceptRefinementFinal.create(PROJECT,r,ConceptRefinementFinal.Outcome.REFINED,seedId,r.getAppliedSelectionRevision(),r.getAppliedBmPlanRevision(),"{}","{}",HASH,OWNER,clock.instant());ReflectionTestUtils.setField(value,"id",501L);return value;}
 ObjectNode proposal(String field,Object current,Object proposed){ObjectNode p=mapper.createObjectNode();p.put("fieldKey",field);p.set("currentValue",mapper.valueToTree(current));p.set("proposedValue",mapper.valueToTree(proposed));p.put("source","MARKET");p.putArray("evidenceIds").add("E");return p;}

 Tagged tagged(String field,Object current,Object proposed){ConceptRefinementRound r=appliedRound(proposal(field,current,proposed),4,3);r.startFinalization("finalize",HASH,"tagged-task",clock.instant());
  ConceptPortfolioSelectionRepository selectionRepo=mock(ConceptPortfolioSelectionRepository.class);ConceptPortfolioHypothesisDecisionRepository hypothesisRepo=mock(ConceptPortfolioHypothesisDecisionRepository.class);
  ConceptLegalRegulatoryReportRepository reportRepo=mock(ConceptLegalRegulatoryReportRepository.class);MarketAnalysisSeedSnapshotRepository seedRepo=mock(MarketAnalysisSeedSnapshotRepository.class);
  TaskRunService runs=mock(TaskRunService.class);ConceptRefinementFinalizationService finalizer=mock(ConceptRefinementFinalizationService.class);
  ConceptPortfolioSelection selection=ConceptPortfolioSelection.create(PROJECT,"run","concept","candidate",HASH,HASH,"selected",HASH,"select",OWNER,clock.instant());ReflectionTestUtils.setField(selection,"id",31L);ReflectionTestUtils.setField(selection,"status",ConceptPortfolioSelectionStatus.READY_FOR_MARKET);ReflectionTestUtils.setField(selection,"hypothesisRevision",4);selection.attachAuxiliaryTask("tagged-task","BUILD_HANDOFF");
  lenient().when(rounds.findByIdForUpdate(101L)).thenReturn(Optional.of(r));lenient().when(selectionRepo.findLocked(31L)).thenReturn(Optional.of(selection));ConceptLegalRegulatoryReport report=mock(ConceptLegalRegulatoryReport.class);lenient().when(report.getId()).thenReturn("legal-current");lenient().when(reportRepo.findBySelectionIdAndStatusAndDeletedAtIsNull(31L,"CURRENT")).thenReturn(Optional.of(report));
  MarketAnalysisSeedSnapshot stale=MarketAnalysisSeedSnapshot.createPortfolio("seed-1",PROJECT,31L,"concept","legal-old","2.0",HASH,HASH,"{\"selectedConcept\":{\"identity\":{\"targetUsers\":\"old\"},\"solution\":{\"featureSet\":[\"old\"]}}}",OWNER,clock.instant());stale.markStale(clock.instant());lenient().when(seedRepo.findByIdAndDeletedAtIsNull("seed-1")).thenReturn(Optional.of(stale));lenient().when(seedRepo.save(any())).thenAnswer(i->i.getArgument(0));
  List<ConceptPortfolioHypothesisDecision> rows=new ArrayList<>();for(PortfolioHypothesisType type:PortfolioHypothesisType.values())rows.add(ConceptPortfolioHypothesisDecision.create(31L,PROJECT,"concept",type,"\"x\"","\"x\"","USER","ACCEPTED",1,true,"VALID",null,"NONE","NOT_REQUIRED",false,OWNER,clock.instant()));lenient().when(hypothesisRepo.findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(31L)).thenReturn(rows);
  BmPlanPreparationService localBm=mock(BmPlanPreparationService.class);lenient().when(localBm.current(PROJECT)).thenReturn(new BmPlanPreparationService.PlanView(mapper.createObjectNode(),mapper.createObjectNode(),3));ConceptRefinementFinal value=mock(ConceptRefinementFinal.class);lenient().when(value.getId()).thenReturn(501L);lenient().when(finalizer.createFinal(anyLong(),anyLong(),any(),any(),anyString(),anyInt(),anyInt(),any(),any())).thenReturn(value);
  ObjectNode cumulativeOverlay=(ObjectNode)contract.applicationPlan(r).path("overlay").deepCopy();ArrayNode cumulativeChanges=(ArrayNode)mapper.readTree(r.getDecisionJson()).path("selectedProposals").deepCopy();lenient().when(finalizer.outcome(r)).thenReturn(new ConceptRefinementFinalizationService.OutcomePlan(ConceptRefinementFinal.Outcome.REFINED,false,false,!cumulativeOverlay.isEmpty(),true,cumulativeOverlay,cumulativeChanges));
  var materializer=new ConceptRefinementFinalizationMaterializationService(rounds,selectionRepo,hypothesisRepo,reportRepo,seedRepo,localBm,contract,finalizer,new ConceptPortfolioJsonHasher(mapper),runs,mapper,clock);
  ObjectNode input=mapper.createObjectNode();input.put("action","BUILD_HANDOFF");ObjectNode binding=input.putObject("refinementFinalization");binding.put("roundId",101L);binding.put("finalizationHash",HASH);binding.put("finalMarketSeedSnapshotId","seed-new");
  ObjectNode result=mapper.createObjectNode();result.put("marketSeedSnapshotHash",HASH);ObjectNode market=result.putObject("handoff").put("compatibility","PASS").putObject("marketAnalysisSeedSnapshot");market.put("contract","market-analysis-seed-snapshot-v1");market.put("schemaVersion","2.0");market.put("snapshotId","seed-new");market.put("sourceSnapshotHash",HASH);ObjectNode concept=market.putObject("selectedConcept");concept.putObject("identity").put("targetUsers","targetUsers".equals(field)?String.valueOf(proposed):"old");ArrayNode features=concept.putObject("solution").putArray("featureSet");features.add("featureSet".equals(field)?String.valueOf(proposed):"old");ObjectNode hs=market.putObject("finalHypotheses");Map<PortfolioHypothesisType,String> names=Map.of(PortfolioHypothesisType.TARGET_REGION,"targetRegion",PortfolioHypothesisType.REVENUE_MODEL,"revenueModel",PortfolioHypothesisType.PRICE,"price",PortfolioHypothesisType.CHANNELS,"channels",PortfolioHypothesisType.DIFFERENTIATORS,"differentiators",PortfolioHypothesisType.PRE_MARKET_SOM_SHARE,"preMarketSomShare",PortfolioHypothesisType.PRE_MARKET_SOM,"preMarketSom");names.values().forEach(name->hs.putObject(name).put("value","x"));
  String hash=new ConceptPortfolioJsonHasher(mapper).productionCompatibleHash(market);result.put("marketSeedSnapshotHash",hash);ExecutionResponse response=new ExecutionResponse("internal-ai-execution-v1",TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION.name(),"1.0","tagged-task","attempt-1","corr",HASH,"1.0",result,null,null,null);TaskRunWorkerContext context=new TaskRunWorkerContext("tagged-task",PROJECT,OWNER,TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION,"CONCEPT_PORTFOLIO_SELECTION","31",input.toString(),HASH,"key","corr","1.0","1.0","ko-KR",1,3);
  return new Tagged(r,selection,materializer,seedRepo,finalizer,runs,input,result,response,new TaskRunService.Claim("tagged-task","attempt-1","claim"),context);}
 void refreshHash(Tagged f){JsonNode market=f.result.at("/handoff/marketAnalysisSeedSnapshot");f.result.put("marketSeedSnapshotHash",new ConceptPortfolioJsonHasher(mapper).productionCompatibleHash(market));}
 record Tagged(ConceptRefinementRound round,ConceptPortfolioSelection selection,ConceptRefinementFinalizationMaterializationService materializer,MarketAnalysisSeedSnapshotRepository seedRepo,ConceptRefinementFinalizationService finalizer,TaskRunService taskRuns,ObjectNode input,ObjectNode result,ExecutionResponse response,TaskRunService.Claim claim,TaskRunWorkerContext context){}
}
