package com.aivle.backend.pipeline.conceptportfolio.selection.application;

import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.*;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.pipeline.refinement.ConceptRefinementRound;
import com.aivle.backend.pipeline.refinement.ConceptRefinementRoundRepository;
import com.aivle.backend.pipeline.refinement.ConceptRefinementService;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionResponse;
import com.aivle.backend.taskrun.service.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ConceptPortfolioSelectionMaterializationService {
    private final ConceptPortfolioSelectionRepository selections;
    private final ConceptPortfolioHypothesisDecisionRepository hypotheses;
    private final ConceptPortfolioDeltaLegalReviewRepository deltas;
    private final ConceptLegalRegulatoryReportRepository reports;
    private final MarketAnalysisSeedSnapshotRepository marketSeeds;
    private final ConceptPortfolioSelectionService selectionService;
    private final ConceptPortfolioJsonHasher hasher;
    private final TaskRunService taskRuns;
    private final ConceptRefinementRoundRepository rounds;
    private final ConceptRefinementService refinement;
    private final ObjectMapper mapper;
    private final Clock clock;
    /** 시드 재발급 때 시장조사 계보를 이어 주는 자리. 모듈 2 파일을 건드리지 않으려고 JPQL 로 쓴다. */
    private final EntityManager entityManager;

    public ConceptPortfolioSelectionMaterializationService(ConceptPortfolioSelectionRepository selections,
            ConceptPortfolioHypothesisDecisionRepository hypotheses,
            ConceptPortfolioDeltaLegalReviewRepository deltas,
            ConceptLegalRegulatoryReportRepository reports,
            MarketAnalysisSeedSnapshotRepository marketSeeds,
            ConceptPortfolioSelectionService selectionService, ConceptPortfolioJsonHasher hasher,
            TaskRunService taskRuns, ConceptRefinementRoundRepository rounds,
            ConceptRefinementService refinement,
            ObjectMapper mapper, Clock clock, EntityManager entityManager) {
        this.selections=selections; this.hypotheses=hypotheses; this.deltas=deltas;
        this.reports=reports; this.marketSeeds=marketSeeds; this.selectionService=selectionService;
        this.hasher=hasher; this.taskRuns=taskRuns; this.rounds=rounds; this.refinement=refinement;
        this.mapper=mapper; this.clock=clock; this.entityManager=entityManager;
    }

    @Transactional
    public String complete(TaskRunService.Claim claim, TaskRunWorkerContext context, ExecutionResponse response) {
        taskRuns.assertActiveClaim(claim.taskRunId(), claim.taskAttemptId(), claim.claimToken());
        JsonNode result=validate(response.result());
        String action=result.path("action").asText();
        JsonNode input=mapper.readTree(context.inputSnapshot());
        require(action.equals(input.path("action").asText()));
        ConceptPortfolioSelection selection = locked(context);
        // 다듬기 액션은 가설 개정을 걸지 않는다 - 선택의 단계가 아니라 그 위에서 도는
        // 루프라, 개정이 그 사이 올라갔다고 라운드를 버리면 안 된다.
        if (!"REFINE_FROM_MARKET".equals(action)) {
            require(input.path("expectedHypothesisRevision").isIntegralNumber()
                && input.path("expectedHypothesisRevision").asInt() == selection.getHypothesisRevision());
        }
        switch(action) {
            case "PREPARE_HYPOTHESES" -> {
                persistInitial(selection,result.path("hypotheses"));
                selection.completeTask(context.taskRunId(),ConceptPortfolioSelectionStatus.PENDING_HYPOTHESIS_CONFIRMATION,true);
                adopt(claim,context,response);
            }
            case "CONFIRM_HYPOTHESES" -> {
                applyHypotheses(selection,result.path("hypotheses"),context.ownerId());
                boolean allReady=selectionService.latestRequired(selection.getId()).stream()
                    .allMatch(value->("ACCEPTED".equals(value.getDecisionStatus())||"USER_EDITED_ACCEPTED".equals(value.getDecisionStatus()))
                        && value.getFinalValueJson()!=null&&"VALID".equals(value.getSemanticStatus()));
                boolean deltaRequired=selectionService.latestRequired(selection.getId()).stream()
                    .anyMatch(value->value.isDeltaLegalRequired()&&"PENDING".equals(value.getLegalReviewStatus()));
                ConceptPortfolioSelectionStatus next=!allReady?ConceptPortfolioSelectionStatus.PENDING_HYPOTHESIS_CONFIRMATION:
                    deltaRequired?ConceptPortfolioSelectionStatus.DELTA_LEGAL_PENDING:ConceptPortfolioSelectionStatus.READY_FOR_LEGAL_REPORT;
                selection.completeTask(context.taskRunId(),next,true); adopt(claim,context,response);
                if(allReady&&deltaRequired) selectionService.queueDelta(context.ownerId(),selection,
                    context.idempotencyKey()+":delta");
            }
            case "PROPOSE_ALTERNATIVE" -> {
                JsonNode item=result.path("alternative"); require(item.isObject());
                PortfolioHypothesisType type=PortfolioHypothesisType.valueOf(item.path("hypothesisType").asText());
                int version=item.path("proposalVersion").asInt();
                hypotheses.save(fromJson(selection,item,type,version,null));
                selection.completeTask(context.taskRunId(),ConceptPortfolioSelectionStatus.PENDING_HYPOTHESIS_CONFIRMATION,true);
                staleDependents(selection.getId()); adopt(claim,context,response);
            }
            case "DELTA_LEGAL" -> {
                JsonNode delta=result.path("deltaLegalResult"); require(delta.isObject());
                boolean approved=delta.path("approved").asBoolean();
                String json=mapper.writeValueAsString(result);
                deltas.save(ConceptPortfolioDeltaLegalReview.create(selection,context.taskRunId(),
                    input.path("expectedHypothesisRevision").asInt(), delta.path("reviewToken").asText(),
                    mapper.writeValueAsString(delta.path("hypothesisTypes")),
                    delta.path("status").asText(),approved,json,hasher.hash(result)));
                if(approved) applyHypotheses(selection,result.path("hypotheses"),context.ownerId());
                boolean allReady = approved && selectionService.latestRequired(selection.getId()).stream()
                    .allMatch(ConceptPortfolioHypothesisDecision::ready);
                selection.completeTask(context.taskRunId(),allReady?ConceptPortfolioSelectionStatus.READY_FOR_LEGAL_REPORT:
                    ConceptPortfolioSelectionStatus.DELTA_LEGAL_FAILED,false); adopt(claim,context,response);
                // 다듬기 루프가 돌고 있으면 **그 라운드에 결과를 적는다**. 안 적으면 라운드가
                // 영영 안 닫히고 다음 걸음이 걸리지 않는다 - 루프가 조용히 멈춘다.
                rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(selection.getId())
                    .filter(open -> open.getLegalOutcome()==null)
                    .ifPresent(open -> open.recordLegal(
                        approved?ConceptRefinementRound.LegalOutcome.PASSED
                            :ConceptRefinementRound.LegalOutcome.BLOCKED,
                        mapper.writeValueAsString(delta.path("reasons"))));
            }
            case "BUILD_HANDOFF" -> {
                JsonNode handoff=result.path("handoff"); JsonNode market=handoff.path("marketAnalysisSeedSnapshot");
                require("PASS".equals(handoff.path("compatibility").asText()));
                require("market-analysis-seed-snapshot-v1".equals(market.path("contract").asText()));
                require("2.0".equals(market.path("schemaVersion").asText()));
                var report=reports.findBySelectionIdAndStatusAndDeletedAtIsNull(selection.getId(),"CURRENT")
                    .orElseThrow(ContractViolation::new);
                String id=market.path("snapshotId").asText(); String snapshotHash=result.path("marketSeedSnapshotHash").asText();
                require(snapshotHash.equals(hasher.productionCompatibleHash(market)));
                // ⚠ **새 시드를 넣기 전에 서 있던 시드를 낡음으로 내린다.** 부분 유니크 인덱스
                //   `uk_market_seed_portfolio_selection`(selection 당 살아 있는 시드 1개)이 있어서,
                //   안 내리면 삽입이 제약에 걸리고 그 실패가 워커의 catch 에서 `AI_RESULT_INVALID`
                //   로 접혀 나간다 — AI 는 부르지도 않았는데 AI 탓으로 보인다(2026-08-19 실측).
                //   가설을 건드린 다듬기는 `staleDependents()` 가 미리 내려 주지만, `featureSet`
                //   처럼 오버레이만 고친 다듬기는 그 길을 안 지나므로 여기가 유일한 자리다.
                List<MarketAnalysisSeedSnapshot> standing =
                    marketSeeds.findAllByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(selection.getId());
                List<String> previousSeedIds = standing.stream().map(MarketAnalysisSeedSnapshot::getId).toList();
                standing.forEach(previous->previous.markStale(Instant.now(clock)));
                // ⚠ **flush 를 빼면 고친 것이 아니다.** Hibernate 의 ActionQueue 는 한 트랜잭션
                //   안에서 INSERT 를 UPDATE 보다 «먼저» 내보낸다. 낡음 처리를 코드에 적어도
                //   flush 없이는 삽입이 앞서 나가 같은 제약에 그대로 걸린다.
                marketSeeds.flush();
                // ⚠ **saveAndFlush 여야 한다.** 아래 계보 이어받기는 벌크 UPDATE 라 영속성
                //   컨텍스트에 걸린 INSERT 를 기다려 주지 않는다. save() 로 두면 새 시드가 아직
                //   DB 에 없는 상태에서 UPDATE 가 먼저 나가 외래키(`fk_market_research_run_seed`)가
                //   깨진다(2026-08-19 실측).
                // ⚠ **`refinementApplied` 를 여기서 명시한다.** 도메인에 기본값 `false` 인 11인자
                //   오버로드가 있고 유일한 호출부가 그것을 쓰고 있었다 — 그래서 이 표가 한 번도
                //   서지 않았고, 그 값을 보는 시장 인터뷰 게이트({@code MarketInterviewBoardService})
                //   가 영영 안 열렸다. 도메인 주석이 「기본값을 만들면 그 기본값이 곧 «다듬기 안
                //   지났음»으로 굳는다」고 경고한 그대로다(2026-08-19 실측).
                //   기준은 «이 selection 이 컨셉 다듬기 라운드를 지났는가» 하나다. 오버레이가
                //   없어도 가설만 수정하거나 제안을 모두 넘긴 뒤 최종 확정할 수 있다.
                boolean refinementApplied = rounds
                    .findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(selection.getId())
                    .isPresent();
                marketSeeds.saveAndFlush(MarketAnalysisSeedSnapshot.createPortfolio(id,selection.getProjectId(),selection.getId(),
                    selection.getConceptId(),report.getId(),"2.0",market.path("sourceSnapshotHash").asText(),snapshotHash,
                    mapper.writeValueAsString(market),context.ownerId(),Instant.now(clock),refinementApplied));
                carrySeedLineageForward(selection,previousSeedIds,id);
                selection.completeTask(context.taskRunId(),ConceptPortfolioSelectionStatus.READY_FOR_MARKET,false);
                adopt(claim,context,response);
            }
            case "REFINE_FROM_MARKET" -> {
                // ⚠ **결과 검증을 여기서 한다.** 이 갈래에는 정합 검사가 없어서, 모델이 칸을
                // 빠뜨려도 AI 호출은 성공하고 제안만 조용히 반쪽으로 저장됐다.
                requireProposals(result.path("refinementProposals"));
                // 라운드를 **DB 에 남긴다**. 워커는 폴링마다 새로 깨어나므로 라운드 번호와
                // 기각 사유를 메모리에 두면 재시작 한 번에 루프가 처음부터 다시 돈다.
                int round = input.path("refinementMaterial").path("round").asInt(1);
                // ⚠ **어느 조사판을 근거로 만든 라운드인지 새긴다.** 이것이 없으면 조사를 다시
                //   돌려도 다듬기가 안 걸려, 화면 왼쪽은 오늘 조사고 오른쪽은 이틀 전 제안이 된다.
                rounds.save(ConceptRefinementRound.of(selection.getProjectId(), selection.getId(), round,
                    mapper.writeValueAsString(result.path("refinementProposals")),
                    mapper.writeValueAsString(result.path("driftRejections")),
                    refinement.currentResearchVersion(selection.getProjectId())));
                // 상태는 그대로 둔다 - 다듬기는 선택의 단계가 아니라 그 위에서 도는 루프다.
                selection.completeTask(context.taskRunId(), selection.getStatus(), false);
                adopt(claim,context,response);
                // ⚠ **제안이 0건이면 그 자리에서 닫는다.** 적용할 것이 없으면 가설 확정도 법률
                //   델타도 안 붙어 **라운드에 법률 결과를 적는 자가 아무도 없다.** 워커는 닫힌
                //   라운드만 보므로 루프가 조용히 멈추고 화면은 영영 「다듬는 중」을 말한다.
                //
                // ⚠⚠ **여기서 적용하지 않는다.** 이 자리가 유일한 자동 적용 지점이었고, 그래서
                //   AI 가 「가격을 9,500원으로」라고 말하는 동시에 이미 올려 버렸다. 사용자가
                //   볼 기회도 거절할 문도 없었다. 적용은 사람이 고른 뒤 decide 가 한다.
                if (result.path("refinementProposals").isEmpty()) {
                    closeRoundWithoutDelta(selection.getId(), false);
                }
            }
            case "NARRATE_REFINED" -> {
                selection.completeTask(context.taskRunId(), selection.getStatus(), false);
                adopt(claim,context,response);
                // ⚠ **검증을 통과한 것만 저장한다.** 통과 못 하면 아무것도 안 남기고, 화면은
                // 칸 나열로 폴백한다 - 반쯤 맞는 문장을 컨셉 원문 자리에 세우면 그것이 곧
                // 지어낸 근거가 된다.
                JsonNode narrative = result.path("narrative");
                String conceptName = input.path("selectedCandidate").path("candidate")
                    .path("conceptName").asText("");
                if (narrativeKeepsConcept(narrative, conceptName)
                        && narrativeMatchesChanges(narrative, selection.getId())) {
                    refinement.recordNarrative(selection.getProjectId(), selection.getId(),
                        mapper.writeValueAsString(narrative));
                }
            }
            default -> throw new ContractViolation();
        }
        return action;
    }

    @Transactional
    public void fail(TaskRunService.Claim claim,TaskRunWorkerContext context,String code,String reason,boolean retryable){
        taskRuns.assertActiveClaim(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken());
        String action=mapper.readTree(context.inputSnapshot()).path("action").asText();
        ConceptPortfolioSelection selection=locked(context);
        selection.failTask(context.taskRunId(),"DELTA_LEGAL".equals(action)?ConceptPortfolioSelectionStatus.DELTA_LEGAL_FAILED:
            ConceptPortfolioSelectionStatus.FAILED,code); taskRuns.fail(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken(),code,reason,retryable);
    }
    private void persistInitial(ConceptPortfolioSelection s,JsonNode array){require(array.isArray()&&array.size()==7);Set<String> types=new HashSet<>();
        for(JsonNode item:array){PortfolioHypothesisType type=PortfolioHypothesisType.valueOf(item.path("hypothesisType").asText());types.add(type.name());hypotheses.save(fromJson(s,item,type,1,null));}
        require(types.size()==7&&types.contains("TARGET_REGION"));}
    private void applyHypotheses(ConceptPortfolioSelection s,JsonNode array,Long user){require(array.isArray()&&array.size()==7);
        for(JsonNode item:array){PortfolioHypothesisType type=PortfolioHypothesisType.valueOf(item.path("hypothesisType").asText());
            ConceptPortfolioHypothesisDecision current=hypotheses.findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(s.getId(),type).orElseThrow(ContractViolation::new);
            JsonNode finalValue=item.get("finalValue");
            current.apply(finalValue==null||finalValue.isNull()?null:canonicalJson(type,finalValue),item.path("source").asText(),item.path("decisionStatus").asText(),item.path("locked").asBoolean(),
                item.path("semanticStatus").asText(),item.path("semanticReason").isNull()?null:item.path("semanticReason").asText(),item.path("legalImpact").asText(),
                item.path("legalReviewStatus").asText(),item.path("deltaLegalRequired").asBoolean(),user,
                item.path("finalValue").isNull()?null:Instant.now(clock));}}
    private ConceptPortfolioHypothesisDecision fromJson(ConceptPortfolioSelection s,JsonNode item,PortfolioHypothesisType type,int version,Long user){
        return ConceptPortfolioHypothesisDecision.create(s.getId(),s.getProjectId(),s.getConceptId(),type,canonicalJson(type,item.path("proposedValue")),
            item.get("finalValue")==null||item.get("finalValue").isNull()?null:canonicalJson(type,item.get("finalValue")),item.path("source").asText(),item.path("decisionStatus").asText(),version,item.path("locked").asBoolean(),
            item.path("semanticStatus").asText("UNASSESSED"),item.path("semanticReason").isMissingNode()||item.path("semanticReason").isNull()?null:item.path("semanticReason").asText(),
            item.path("legalImpact").asText("NONE"),item.path("legalReviewStatus").asText("NOT_REQUIRED"),item.path("deltaLegalRequired").asBoolean(false),user,null);}
    private String canonicalJson(PortfolioHypothesisType type,JsonNode value){
        try{return mapper.writeValueAsString(HypothesisValueContract.canonicalize(mapper,type,value));}
        catch(IllegalArgumentException invalid){throw new ContractViolation();}}
    private String nullableJson(JsonNode value){return value==null||value.isNull()?null:mapper.writeValueAsString(value);}
    private static void requireProposals(JsonNode proposals){
        require(proposals.isArray());
        for(JsonNode proposal:proposals){
            require(!proposal.path("fieldKey").asText("").isBlank());
            require(!proposal.path("afterText").asText("").isBlank());
        }
    }

    private void closeRoundWithoutDelta(Long selectionId, boolean requireDecided){
        rounds.findTopBySelectionIdAndDeletedAtIsNullOrderByRoundDesc(selectionId)
            // ⚠ **아직 아무도 결정하지 않은 라운드를 닫지 않는다.** 다듬기와 무관한 일반 가설
            // 확정도 이 자리를 지나는데, 그때 열린 라운드를 PASSED 로 닫으면 화면이 「다듬기
            // 완료 - 법률 검토까지 통과했어요」라고 말한다. **적용된 것은 0건인데** 그렇다.
            .filter(open -> !requireDecided || open.getAcceptedFieldsJson()!=null)
            .filter(open -> open.getLegalOutcome()==null)
            .ifPresent(open -> open.recordLegal(ConceptRefinementRound.LegalOutcome.PASSED, "[]"));
    }

    private JsonNode validate(JsonNode result){require(result!=null&&result.isObject());require("concept-portfolio-v2-selection-action-result-v1".equals(result.path("contract").asText()));require("1.0".equals(result.path("schemaVersion").asText()));return result;}
    private ConceptPortfolioSelection locked(TaskRunWorkerContext c){ConceptPortfolioSelection s=selections.findLocked(Long.valueOf(c.subjectId())).orElseThrow(ContractViolation::new);
        require(s.isCurrent()&&s.getProjectId().equals(c.projectId())&&c.taskRunId().equals(s.getActiveTaskRunId()));return s;}
    private ConceptPortfolioSelection lockedForRefinement(TaskRunWorkerContext c){ConceptPortfolioSelection s=selections.findLocked(Long.valueOf(c.subjectId())).orElseThrow(ContractViolation::new);
        require(s.getProjectId().equals(c.projectId()));return s;}
    private void adopt(TaskRunService.Claim claim,TaskRunWorkerContext c,ExecutionResponse r){taskRuns.adopt(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken(),mapper.writeValueAsString(r.result()),c.inputHash(),r.resultSchemaVersion());}
    /**
     * <b>오버레이만 바뀐 재발급은 시장조사를 버리지 않는다.</b>
     *
     * <p>낡음 판정({@code MarketResearchService:202})은 «조사가 묶인 시드 id 와 지금 시드 id 가
     * 같은가»만 본다. 그래서 다듬기를 반영해 시드를 다시 세우면 조사가 통째로 낡음이 되고,
     * 재무({@code FinancialService:458})가 <b>current Market Research 결과가 필요합니다</b> 로
     * 막힌다 — 20분·약 470콜을 다시 사야 풀린다.
     *
     * <p>그런데 <b>그 다듬기 제안을 만든 것이 바로 그 조사다</b>({@code concept_refinement_rounds}
     * 가 어느 조사판을 근거로 만든 라운드인지 새긴다). 조사가 낳은 결론을 받아들였다는 이유로
     * 그 조사를 버리는 것은 순환이다. 그래서 <b>가설이 그대로인 재발급</b>에 한해 계보를
     * 이어받는다 — 가설이 바뀌었다면 {@code sourceSelectionRevision} 이 어긋나 여기서 걸러진다.
     *
     * <p>⚠ 모듈 2({@code pipeline.market})는 main 과 바이트 동일해야 하므로 그쪽 파일을 고치지
     * 않는다. 그래서 리포지터리에 조회 메서드를 더하지 않고 JPQL 로 직접 쓴다.
     */
    private void carrySeedLineageForward(ConceptPortfolioSelection selection,
            List<String> previousSeedIds, String newSeedId) {
        if (previousSeedIds.isEmpty() || newSeedId == null || newSeedId.isBlank()) return;
        entityManager.createQuery("update MarketResearchRun r set r.sourceMarketSeedSnapshotId = :newSeed "
                + "where r.sourcePortfolioSelectionId = :selectionId "
                + "and r.sourceMarketSeedSnapshotId in :previousSeeds "
                + "and r.sourceSelectionRevision = :revision and r.deletedAt is null")
            .setParameter("newSeed", newSeedId)
            .setParameter("selectionId", selection.getId())
            .setParameter("previousSeeds", previousSeedIds)
            .setParameter("revision", selection.getHypothesisRevision())
            .executeUpdate();
    }

    private void staleDependents(Long id){reports.findAllBySelectionIdAndStatusAndDeletedAtIsNull(id,"CURRENT").forEach(ConceptLegalRegulatoryReport::markStale);marketSeeds.findAllByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(id).forEach(v->v.markStale(Instant.now(clock)));}
    /**
     * 서술문이 <b>실제로 바뀐 값을 담았나</b>. 채택된 변경의 「바뀐 말」을 잣대로 쓴다.
     *
     * <p>⚠⚠ <b>채택분만 잣대로 쓴다.</b> 사람이 고르는 문이 생긴 뒤로 {@code proposal_json}
     * 전량은 「제안된 것」이지 「반영된 것」이 아니다. 전량을 쓰면 모델에게 <b>사용자가
     * 체크하지 않은 변경까지 최종 컨셉 문장에 담으라</b>고 시키게 된다.
     */
    private boolean narrativeMatchesChanges(JsonNode narrative, Long selectionId){
        List<String> marks=new ArrayList<>();
        for(ConceptRefinementRound round:rounds.findBySelectionIdAndDeletedAtIsNullOrderByRoundAsc(selectionId)){
            if(round.getProposalJson()==null)continue;
            java.util.Set<String> accepted = refinement.acceptedOf(round);
            for(JsonNode proposal:mapper.readTree(round.getProposalJson())){
                // 옛 라운드(결정 칸이 없던 시절)는 전량이 이미 적용된 것이라 그대로 센다.
                if(round.getAcceptedFieldsJson()!=null
                    && !accepted.contains(proposal.path("fieldKey").asText()))continue;
                // ⚠ 잣대는 값 전체가 아니라 **바뀐 말**이다. 같은 계산을 서술문 입력에도 쓴다 -
                // 두 곳이 갈리면 모델은 A 를 담으라는 말을 듣고 서버는 B 를 찾는다.
                marks.add(ConceptRefinementService.changeMark(
                    proposal.path("beforeText").asText(""), proposal.path("afterText").asText("")));
            }
        }
        return narrativeMatchesChanges(narrative, marks);
    }

    /**
     * {@code afterText} 대조는 「바뀐 값을 담았나」만 본다 - 바뀌지 <b>않은</b> 부분은 아무
     * 검사도 안 받는다. 그 틈으로 모델이 다른 사업을 써 넣을 수 있다. 사업안 이름이 문단 안에
     * 그대로 남아 있는지가 그것을 막는 <b>가장 싼 잣대</b>다.
     *
     * <p>이름을 모르면(입력에 없으면) 통과시킨다 - 없는 잣대로 기각하면 서술문이 영영 안 선다.
     */
    static boolean narrativeKeepsConcept(JsonNode narrative, String conceptName){
        String name=squeeze(conceptName);
        if(name.isEmpty())return true;
        StringBuilder whole=new StringBuilder();
        for(JsonNode segment:narrative)whole.append(segment.path("text").asText(""));
        return squeeze(whole.toString()).contains(name);
    }

    /**
     * 판정 그 자체 - <b>조회 없이</b> 돈다. 여기가 느슨해지면 LLM 이 쓴 아무 문장이나
     * 컨셉 원문 자리에 선다.
     *
     * @param afterTexts 채택된 변경의 바뀐 말 - <b>화면의 번호와 같은 순서</b>여야 한다
     */
    static boolean narrativeMatchesChanges(JsonNode narrative, List<String> afterTexts){
        if(narrative==null||!narrative.isArray()||narrative.isEmpty())return false;
        Set<Integer> seen=new HashSet<>();
        for(JsonNode segment:narrative){
            JsonNode ref=segment.path("changeRef");
            if(!ref.isInt())continue;
            int at=ref.asInt();
            if(at<1||at>afterTexts.size())return false;
            if(!seen.add(at))return false;
            String expected=squeeze(afterTexts.get(at-1));
            if(expected.isEmpty()||!squeeze(segment.path("text").asText("")).contains(expected))return false;
        }
        return true;
    }

    /** 공백을 지운 문자열. 조사·띄어쓰기 손질까지 어긋남으로 세지 않기 위한 것이다. */
    private static String squeeze(String value){return value==null?"":value.replaceAll("\\s+","");}

    private static void require(boolean condition){if(!condition)throw new ContractViolation();}
    public static final class ContractViolation extends RuntimeException { }
}
