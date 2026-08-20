package com.aivle.backend.pipeline.conceptportfolio.selection.application;

import static com.aivle.backend.pipeline.conceptportfolio.selection.api.ConceptPortfolioSelectionApiModels.*;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.application.*;
import com.aivle.backend.pipeline.conceptportfolio.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.repository.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.api.ConceptPortfolioSelectionApiModels.ActionAccepted;
import com.aivle.backend.pipeline.conceptportfolio.selection.api.ConceptPortfolioSelectionApiModels.ActionRequest;
import com.aivle.backend.pipeline.conceptportfolio.selection.api.ConceptPortfolioSelectionApiModels.ConfirmHypothesesRequest;
import com.aivle.backend.pipeline.conceptportfolio.selection.api.ConceptPortfolioSelectionApiModels.CreateSelectionRequest;
import com.aivle.backend.pipeline.conceptportfolio.selection.api.ConceptPortfolioSelectionApiModels.HypothesisView;
import com.aivle.backend.pipeline.conceptportfolio.selection.api.ConceptPortfolioSelectionApiModels.LegalReportView;
import com.aivle.backend.pipeline.conceptportfolio.selection.api.ConceptPortfolioSelectionApiModels.MarketSeedView;
import com.aivle.backend.pipeline.conceptportfolio.selection.api.ConceptPortfolioSelectionApiModels.SelectionView;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.*;
import com.aivle.backend.pipeline.idea.repository.IdeaBriefFieldRepository;
import com.aivle.backend.pipeline.marketseed.domain.MarketAnalysisSeedSnapshot;
import com.aivle.backend.pipeline.marketseed.repository.MarketAnalysisSeedSnapshotRepository;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.TaskRun;
import com.aivle.backend.taskrun.service.TaskRunService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;

@Service
public class ConceptPortfolioSelectionService {
    private static final Set<ConceptPortfolioRunStatus> SELECTABLE_RUNS = Set.of(
        ConceptPortfolioRunStatus.RESULTS_AVAILABLE, ConceptPortfolioRunStatus.RESULTS_WITH_OPEN_INPUT);
    private final ProjectRepository projects;
    private final ConceptPortfolioRunRepository runs;
    private final ConceptPortfolioConceptRepository concepts;
    private final ConceptPortfolioSelectionRepository selections;
    private final ConceptPortfolioHypothesisDecisionRepository hypotheses;
    private final ConceptPortfolioDeltaLegalReviewRepository deltas;
    private final ConceptLegalRegulatoryReportRepository reports;
    private final MarketAnalysisSeedSnapshotRepository marketSeeds;
    private final IdeaBriefFieldRepository briefFields;
    private final ConceptPortfolioSeedBuilder seedBuilder;
    private final ConceptPortfolioSelectionTaskFactory tasks;
    private final TaskRunService taskRuns;
    private final ConceptPortfolioJsonHasher hasher;
    /** 다듬기가 남긴 오버레이 — 최종 확정 때 시드에 실어 보낸다. */
    private final com.aivle.backend.pipeline.refinement.ConceptRefinementFinalRepository refinementFinals;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ConceptPortfolioSelectionService(ProjectRepository projects, ConceptPortfolioRunRepository runs,
            ConceptPortfolioConceptRepository concepts, ConceptPortfolioSelectionRepository selections,
            ConceptPortfolioHypothesisDecisionRepository hypotheses,
            ConceptPortfolioDeltaLegalReviewRepository deltas,
            ConceptLegalRegulatoryReportRepository reports,
            MarketAnalysisSeedSnapshotRepository marketSeeds, IdeaBriefFieldRepository briefFields,
            ConceptPortfolioSeedBuilder seedBuilder, ConceptPortfolioSelectionTaskFactory tasks,
            TaskRunService taskRuns,
            ConceptPortfolioJsonHasher hasher,
            com.aivle.backend.pipeline.refinement.ConceptRefinementFinalRepository refinementFinals,
            ObjectMapper mapper, Clock clock) {
        this.projects=projects; this.runs=runs; this.concepts=concepts; this.selections=selections;
        this.hypotheses=hypotheses; this.deltas=deltas; this.reports=reports; this.marketSeeds=marketSeeds;
        this.briefFields=briefFields; this.seedBuilder=seedBuilder; this.tasks=tasks; this.taskRuns=taskRuns;
        this.hasher=hasher; this.refinementFinals=refinementFinals; this.mapper=mapper; this.clock=clock;
    }

    @Transactional
    public SelectionView select(Long ownerId, Long projectId, CreateSelectionRequest body) {
        requireOwnedForUpdate(ownerId, projectId);
        ConceptPortfolioRun run = runs.findOwned(ownerId, projectId, body.runId())
            .filter(value -> value.isCurrent() && SELECTABLE_RUNS.contains(value.getProductStatus()))
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_NOT_SELECTABLE));
        ConceptPortfolioConcept concept = concepts.findByIdAndProjectIdAndDeletedAtIsNull(body.conceptId(), projectId)
            .filter(value -> value.getRun().getId().equals(run.getId()) && value.isSelectable())
            .orElseThrow(() -> new BusinessException(ErrorCode.CONCEPT_NOT_SELECTABLE));
        ObjectNode fingerprint = mapper.createObjectNode();
        fingerprint.put("runId", run.getId()); fingerprint.put("conceptId", concept.getId());
        fingerprint.put("selectionReason", body.selectionReason().strip());
        String requestHash = hasher.hash(fingerprint);
        var replay = selections.findByProjectIdAndIdempotencyKeyAndDeletedAtIsNull(projectId, body.idempotencyKey());
        if (replay.isPresent()) {
            if (!replay.get().getRequestHash().equals(requestHash)) throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            return view(replay.get());
        }
        selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(projectId).ifPresent(previous -> {
            staleDependents(previous.getId());
            if (previous.getActiveTaskRunId() != null) {
                try { taskRuns.cancel(ownerId, projectId, previous.getActiveTaskRunId()); }
                catch (RuntimeException ignored) { /* 늦은 결과는 selection claim gate가 차단한다. */ }
            }
            previous.markStale();
        });
        selections.flush();
        JsonNode legal = mapper.readTree(concept.getLegalReviewJson());
        ConceptPortfolioSelection selection = selections.saveAndFlush(ConceptPortfolioSelection.create(
            projectId, run.getId(), concept.getId(), concept.getCandidateId(), concept.getCanonicalHash(),
            hasher.hash(legal), body.selectionReason().strip(), requestHash, body.idempotencyKey(),
            ownerId, Instant.now(clock)));
        ObjectNode input = baseInput("PREPARE_HYPOTHESES", run, concept);
        TaskRun task = tasks.create(ownerId, selection, "PREPARE_HYPOTHESES", input,
            body.idempotencyKey() + ":prepare", null);
        return view(selection, concept.getConceptName(), task.getId());
    }

    @Transactional(readOnly=true)
    public SelectionView current(Long ownerId, Long projectId) {
        requireOwned(ownerId, projectId); return view(currentSelection(projectId));
    }

    @Transactional(readOnly=true)
    public SelectionView get(Long ownerId, Long projectId, Long selectionId) {
        return view(requireSelection(ownerId, projectId, selectionId));
    }

    @Transactional(readOnly=true)
    public List<HypothesisView> hypotheses(Long ownerId, Long projectId, Long selectionId) {
        requireSelection(ownerId, projectId, selectionId); return latest(selectionId).stream().map(this::hypothesisView).toList();
    }

    @Transactional
    public ActionAccepted confirm(Long ownerId, Long projectId, Long selectionId, ConfirmHypothesesRequest body) {
        ConceptPortfolioSelection selection = lockedCurrent(ownerId, projectId, selectionId);
        ActionAccepted replay = activeReplay(ownerId, projectId, selection, "CONFIRM_HYPOTHESES", body.idempotencyKey());
        if (replay != null) return replay;
        if (!body.changes().isObject() || body.changes().size() > 7)
            throw new BusinessException(ErrorCode.HYPOTHESIS_VALUE_INVALID);
        ObjectNode input = mapper.createObjectNode(); input.put("action", "CONFIRM_HYPOTHESES");
        input.put("expectedHypothesisRevision", selection.getHypothesisRevision());
        input.set("hypotheses", hypothesisArray(latestRequired(selectionId)));
        input.set("edits", body.changes().deepCopy()); input.put("confirmAll", true);
        TaskRun task = tasks.create(ownerId, selection, "CONFIRM_HYPOTHESES", input,
            body.idempotencyKey(), null);
        staleDependents(selectionId);
        return new ActionAccepted(selectionId, "CONFIRM_HYPOTHESES", task.getId(), selection.getStatus().name());
    }

    @Transactional
    public ActionAccepted alternative(Long ownerId, Long projectId, Long selectionId,
            String typeText, ActionRequest body) {
        ConceptPortfolioSelection selection = lockedCurrent(ownerId, projectId, selectionId);
        ActionAccepted replay = activeReplay(ownerId, projectId, selection, "PROPOSE_ALTERNATIVE", body.idempotencyKey());
        if (replay != null) return replay;
        PortfolioHypothesisType type = hypothesisType(typeText);
        ConceptPortfolioHypothesisDecision current = hypotheses
            .findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(selectionId, type)
            .orElseThrow(() -> new BusinessException(ErrorCode.HYPOTHESIS_NOT_FOUND));
        if (current.isLocked()) throw new BusinessException(ErrorCode.HYPOTHESIS_LOCKED);
        ConceptPortfolioConcept concept = concept(selection);
        ObjectNode input = mapper.createObjectNode(); input.put("action", "PROPOSE_ALTERNATIVE");
        input.put("expectedHypothesisRevision", selection.getHypothesisRevision());
        input.set("selectedCandidate", mapper.readTree(concept.getCandidateSnapshotJson()));
        input.put("hypothesisType", type.name()); input.set("rejectedValue", mapper.readTree(current.getProposedValueJson()));
        input.put("proposalVersion", current.getProposalVersion()+1);
        TaskRun task = tasks.create(ownerId, selection, "PROPOSE_ALTERNATIVE", input, body.idempotencyKey(), null);
        staleDependents(selectionId);
        return new ActionAccepted(selectionId, "PROPOSE_ALTERNATIVE", task.getId(), selection.getStatus().name());
    }

    @Transactional
    public ActionAccepted retryDelta(Long ownerId, Long projectId, Long selectionId, ActionRequest body) {
        ConceptPortfolioSelection selection = lockedCurrent(ownerId, projectId, selectionId);
        ActionAccepted replay = activeReplay(ownerId, projectId, selection, "DELTA_LEGAL", body.idempotencyKey());
        if (replay != null) return replay;
        if (selection.getStatus()!=ConceptPortfolioSelectionStatus.DELTA_LEGAL_FAILED)
            throw new BusinessException(ErrorCode.JOB_RETRY_NOT_ALLOWED);
        TaskRun task = queueDelta(ownerId, selection, body.idempotencyKey());
        return new ActionAccepted(selectionId, "DELTA_LEGAL", task.getId(), selection.getStatus().name());
    }

    @Transactional
    public LegalReportView finalizeReport(Long ownerId, Long projectId, Long selectionId) {
        ConceptPortfolioSelection selection = lockedCurrent(ownerId, projectId, selectionId);
        var existing = reports.findBySelectionIdAndStatusAndDeletedAtIsNull(selectionId, "CURRENT");
        if (existing.isPresent()) return reportView(existing.get());
        if (selection.getStatus()!=ConceptPortfolioSelectionStatus.READY_FOR_LEGAL_REPORT)
            throw new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE);
        List<ConceptPortfolioHypothesisDecision> current = latestRequired(selectionId);
        if (!current.stream().allMatch(ConceptPortfolioHypothesisDecision::ready))
            throw new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE);
        ConceptPortfolioConcept concept = concept(selection);
        JsonNode legal = mapper.readTree(concept.getLegalReviewJson());
        if (!"ACCEPT".equals(legal.path("route").asText())) throw new BusinessException(ErrorCode.CONCEPT_NOT_SELECTABLE);
        ArrayNode deltaHistory = mapper.createArrayNode();
        deltas.findAllBySelectionIdAndDeletedAtIsNullOrderByCreatedAtAsc(selectionId)
            .forEach(value -> deltaHistory.add(mapper.readTree(value.getLegalReviewJson())));
        ObjectNode report = mapper.createObjectNode();
        report.put("reportId", "pending"); report.put("selectionId", selectionId); report.put("conceptId", concept.getId());
        report.put("basisDate", LocalDate.now(clock).toString());
        JsonNode selectedConcept = mapper.readTree(concept.getCandidateSnapshotJson()).path("candidate");
        report.set("selectedConcept", selectedConcept.deepCopy());
        ObjectNode roles = report.putObject("businessRoles");
        for (String field : List.of("platformRole", "sellerRole", "providerRole", "intermediaryRole"))
            roles.set(field, selectedConcept.path(field).deepCopy());
        for (String field : List.of("transactionFlow", "paymentFlow", "personalDataUsage",
                "physicalActivities", "partnerRequirements", "qualificationRequirements"))
            report.set(field, selectedConcept.path(field).deepCopy());
        report.set("finalHypotheses", hypothesisArray(current));
        report.set("finalLegalConclusion", legal.deepCopy());
        copyLegal(report, legal, "requiredControls", "requiredDisclosures", "prohibitedVariants",
            "requiredPartnersAndQualifications", "unknownFacts", "officialEvidenceReferences");
        ObjectNode advertising = report.putObject("advertisingExpressionCautions");
        advertising.set("allowedClaims", selectedConcept.path("advertisingClaims").deepCopy());
        advertising.set("prohibitedVariants", legal.path("prohibitedVariants").deepCopy());
        advertising.set("requiredDisclosures", legal.path("requiredDisclosures").deepCopy());
        report.set("deltaLegalHistory", deltaHistory);
        ObjectNode sourceHashes = report.putObject("sourceHashes");
        sourceHashes.put("selectedConcept", selection.getSelectedConceptHash());
        sourceHashes.put("hypotheses", hasher.hash(hypothesisArray(current)));
        sourceHashes.put("baseLegal", selection.getBaseLegalHash());
        String deltaHash = deltaHistory.isEmpty()?null:hasher.hash(deltaHistory);
        if (deltaHash!=null) sourceHashes.put("deltaLegal", deltaHash);
        String evidenceHash = hasher.hash(legal.path("officialEvidenceReferences"));
        sourceHashes.put("officialEvidence", evidenceHash);
        String reportId = UUID.randomUUID().toString(); report.put("reportId", reportId);
        String reportHash = hasher.hash(report);
        ConceptLegalRegulatoryReport saved = reports.save(ConceptLegalRegulatoryReport.create(reportId, selection,
            hasher.hash(hypothesisArray(current)), deltaHash, evidenceHash, mapper.writeValueAsString(report),
            reportHash, ownerId, LocalDate.now(clock)));
        selection.reportReady(); return reportView(saved);
    }

    @Transactional(readOnly=true)
    public LegalReportView currentReport(Long ownerId, Long projectId, Long selectionId) {
        requireSelection(ownerId, projectId, selectionId);
        return reports.findBySelectionIdAndStatusAndDeletedAtIsNull(selectionId, "CURRENT")
            .map(this::reportView).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional
    public ActionAccepted finalizeMarketSeed(Long ownerId, Long projectId, Long selectionId, ActionRequest body) {
        ConceptPortfolioSelection selection = lockedCurrent(ownerId, projectId, selectionId);
        boolean alreadyFinalized = marketSeeds
            .findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(selectionId)
            .filter(MarketAnalysisSeedSnapshot::isRefinementApplied)
            .isPresent();

        if (alreadyFinalized) {
            return new ActionAccepted(
                selectionId,
                "BUILD_HANDOFF",
                null,
                selection.getStatus().name()
            );
        }
        ActionAccepted replay = activeReplay(ownerId, projectId, selection, "BUILD_HANDOFF", body.idempotencyKey());
        if (replay != null) return replay;
        ConceptLegalRegulatoryReport report = reports.findBySelectionIdAndStatusAndDeletedAtIsNull(selectionId, "CURRENT")
            .orElseThrow(() -> new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE));
        // 두 갈래가 이 문을 지난다.
        //   ① 처음 확정 — 가설을 확정하고 법률 보고서를 받은 직후(LEGAL_REPORT_READY).
        //   ② **다시 확정** — 다듬기가 끝난 뒤 「이 컨셉으로 확정하기」. 이때 상태는 이미
        //      READY_FOR_MARKET 이다.
        // ②를 막고 있던 것이 실제 결함이었다. 다듬기가 `targetUsers`·`featureSet` 만 고치면
        // `ConceptRefinementApplyService.apply()` 가 `confirm()` 을 안 부르고(가설이 아니라
        // 오버레이라서), 그러면 상태가 READY_FOR_MARKET 에 머문다. 그 상태에서 확정을 거절하니
        // **다듬어진 두 칸이 시드에 영영 못 실렸다.** 가설을 안 건드린 다듬기는 법률 델타가
        // 필요 없으므로(오버레이 2칸은 법률과 무관하다) 살아 있는 CURRENT 보고서로 충분하다.
        //   ③ **직전 확정이 실패해 FAILED 로 남은 경우.** 실패는 상태를 FAILED 로 남기는데
        //      그것을 되돌리는 문이 어디에도 없다. 그래서 확정이 한 번 깨지면 사용자는 같은
        //      버튼을 영영 못 누른다 — 화면은 409 만 반복한다(2026-08-19 실측: 시드 유니크
        //      충돌로 깨진 뒤 재시도가 전부 거절됐다). 확정은 재시도할 수 있어야 한다.
        //      ⚠ **가설 7종이 모두 확정된 때만 연다.** CONFIRM_HYPOTHESES 가 실패해 FAILED 인
        //      경우까지 열면 가설이 덜 정해진 사업안이 확정된다 — 상태만 보고 열면 그 둘을
        //      구별하지 못하므로 결정 완료 여부를 직접 확인한다.
        boolean retryAfterFailure = selection.getStatus()==ConceptPortfolioSelectionStatus.FAILED
            && latestRequired(selectionId).stream().allMatch(ConceptPortfolioHypothesisDecision::ready);
        if (selection.getStatus()!=ConceptPortfolioSelectionStatus.LEGAL_REPORT_READY
                && selection.getStatus()!=ConceptPortfolioSelectionStatus.READY_FOR_MARKET
                && !retryAfterFailure)
            throw new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE);
        ConceptPortfolioRun run = runs.findLocked(selection.getRunId()).orElseThrow();
        ConceptPortfolioConcept concept = concept(selection);
        ObjectNode input = baseInput("BUILD_HANDOFF", run, concept);
        input.set("hypotheses", hypothesisArray(latestRequired(selectionId)));
        ArrayNode approved = input.putArray("approvedDeltaLegalResults");
        deltas.findFirstBySelectionIdAndHypothesisRevisionAndApprovedTrueAndDeletedAtIsNullOrderByCreatedAtDesc(
                selectionId, selection.getHypothesisRevision())
            .ifPresent(value -> approved.add(
                mapper.readTree(value.getLegalReviewJson()).path("deltaLegalResult")));
        // 다듬기가 고쳤지만 가설도 BM 계획도 아닌 칸(`targetUsers`·`featureSet`)을 실어 보낸다.
        // ⚠ **AI 가 시드를 만들 때 얹어야 한다.** 여기서 만든 뒤 Java 가 덮으면 해시 검증
        // (`snapshotHash.equals(productionCompatibleHash(market))`)이 깨져 저장이 막힌다.
        refinementFinals.findBySelectionIdAndDeletedAtIsNull(selectionId)
            .map(com.aivle.backend.pipeline.refinement.ConceptRefinementFinal::getOverlayJson)
            .filter(json -> json != null && !json.isBlank())
            .ifPresent(json -> input.set("refinementOverlay", mapper.readTree(json)));
        String snapshotId = UUID.nameUUIDFromBytes(
            ("market-seed:" + selectionId + ":" + body.idempotencyKey())
                .getBytes(StandardCharsets.UTF_8)
        ).toString(); 
        ObjectNode binding=input.putObject("productionBinding");
        binding.put("projectId", projectId); binding.put("portfolioSelectionId", selectionId);
        binding.put("portfolioConceptId", concept.getId()); binding.put("marketSeedSnapshotId", snapshotId);
        TaskRun task=tasks.create(ownerId, selection, "BUILD_HANDOFF", input, body.idempotencyKey(), null);
        return new ActionAccepted(selectionId, "BUILD_HANDOFF", task.getId(), selection.getStatus().name());
    }

    @Transactional(readOnly=true)
    public MarketSeedView currentMarketSeed(Long ownerId, Long projectId, Long selectionId) {
        requireSelection(ownerId, projectId, selectionId);
        MarketAnalysisSeedSnapshot value=marketSeeds.findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(selectionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return new MarketSeedView("market-analysis-seed-snapshot-v1", value.getId(), value.getSchemaVersion(),
            value.getProjectId(), value.getPortfolioSelectionId(), value.getPortfolioConceptId(), value.getLegalReportId(),
            value.getSourceSnapshotHash(), value.getSnapshotHash(), value.getFinalizedAt(), mapper.readTree(value.getSnapshotJson()));
    }

    /**
     * 다듬기가 거는 두 액션({@code REFINE_FROM_MARKET}·{@code NARRATE_REFINED})의 <b>입력 뼈대</b>.
     *
     * <p>⚠ <b>{@code ConceptPortfolioSelectionTaskFactory} 는 입력을 채워 주지 않는다.</b> 그냥
     * 받은 것을 해시해서 넘길 뿐이다. 그래서 액션마다 자기 입력을 온전히 만들어야 하는데,
     * 다듬기는 {@code selectedCandidate} 없이 보내고 있었다 — AI 입력 계약이
     * {@code REQUEST_SCHEMA_INVALID} 로 거부하고 라운드가 서지 않았다(2026-08-13 실측:
     * 라운드 0행). 여기를 지나면 그 칸이 반드시 실린다.
     */
    public ObjectNode refinementInput(String action, ConceptPortfolioSelection selection) {
        ConceptPortfolioRun run = runs.findById(selection.getRunId()).orElseThrow();
        return baseInput(action, run, concept(selection));
    }

    TaskRun queueDelta(Long ownerId, ConceptPortfolioSelection selection, String key) {
        ConceptPortfolioRun run=runs.findLocked(selection.getRunId()).orElseThrow(); ConceptPortfolioConcept concept=concept(selection);
        ObjectNode input=baseInput("DELTA_LEGAL", run, concept); input.set("hypotheses", hypothesisArray(latestRequired(selection.getId())));
        return tasks.create(ownerId, selection, "DELTA_LEGAL", input, key, null);
    }
    ObjectNode baseInput(String action, ConceptPortfolioRun run, ConceptPortfolioConcept concept) {
        ObjectNode input=mapper.createObjectNode(); input.put("action", action);
        selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(run.getProject().getId())
            .filter(value -> value.getRunId().equals(run.getId()))
            .ifPresent(value -> input.put("expectedHypothesisRevision", value.getHypothesisRevision()));
        input.set("seed", seedBuilder.build(run.getSourceIdeaBrief(),
            briefFields.findAllByBriefIdOrderById(run.getSourceIdeaBrief().getId()), run.getRequestedMaxConcepts()).value().path("seed").deepCopy());
        input.set("selectedCandidate", mapper.readTree(concept.getCandidateSnapshotJson()));
        input.set("baseLegalReview", mapper.readTree(concept.getLegalReviewJson())); return input;
    }
    List<ConceptPortfolioHypothesisDecision> latestRequired(Long id) {
        List<ConceptPortfolioHypothesisDecision> values=latest(id);
        if(values.size()!=7) throw new BusinessException(ErrorCode.HYPOTHESIS_DECISIONS_INCOMPLETE); return values;
    }
    List<ConceptPortfolioHypothesisDecision> latest(Long id) {
        Map<PortfolioHypothesisType,ConceptPortfolioHypothesisDecision> map=new EnumMap<>(PortfolioHypothesisType.class);
        hypotheses.findAllBySelectionIdAndDeletedAtIsNullOrderByHypothesisTypeAscProposalVersionDesc(id)
            .forEach(value->map.putIfAbsent(value.getHypothesisType(),value));
        return Arrays.stream(PortfolioHypothesisType.values()).map(map::get).filter(Objects::nonNull).toList();
    }
    ArrayNode hypothesisArray(List<ConceptPortfolioHypothesisDecision> values) {
        ArrayNode array=mapper.createArrayNode(); values.forEach(value->{ ObjectNode item=array.addObject();
            item.put("hypothesisType",value.getHypothesisType().name()); item.set("proposedValue",mapper.readTree(value.getProposedValueJson()));
            if(value.getFinalValueJson()==null)item.putNull("finalValue");else item.set("finalValue",mapper.readTree(value.getFinalValueJson()));
            item.put("source",value.getSource()); item.put("decisionStatus",value.getDecisionStatus()); item.put("proposalVersion",value.getProposalVersion());
            item.put("locked",value.isLocked()); item.put("legalImpact",value.getLegalImpact()); item.put("legalReviewStatus",value.getLegalReviewStatus());
            item.put("deltaLegalRequired",value.isDeltaLegalRequired()); item.put("semanticStatus",value.getSemanticStatus());
            if(value.getSemanticReason()==null)item.putNull("semanticReason");else item.put("semanticReason",value.getSemanticReason()); }); return array;
    }
    private void staleDependents(Long id) { reports.findAllBySelectionIdAndStatusAndDeletedAtIsNull(id,"CURRENT").forEach(ConceptLegalRegulatoryReport::markStale);
        marketSeeds.findAllByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(id).forEach(value->value.markStale(Instant.now(clock))); }
    private ConceptPortfolioSelection lockedCurrent(Long owner,Long project,Long id){ requireOwned(owner,project); ConceptPortfolioSelection value=selections.findLocked(id).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if(!value.getProjectId().equals(project)||!value.isCurrent())throw new BusinessException(ErrorCode.MODULE_INPUT_STALE); return value; }
    private ConceptPortfolioSelection requireSelection(Long owner,Long project,Long id){requireOwned(owner,project);return selections.findByIdAndProjectIdAndDeletedAtIsNull(id,project).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
    private ConceptPortfolioSelection currentSelection(Long project){return selections.findByProjectIdAndIsCurrentTrueAndDeletedAtIsNull(project).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
    private ConceptPortfolioConcept concept(ConceptPortfolioSelection selection){return concepts.findByIdAndProjectIdAndDeletedAtIsNull(selection.getConceptId(),selection.getProjectId()).orElseThrow();}
    private PortfolioHypothesisType hypothesisType(String text){try{return PortfolioHypothesisType.valueOf(text);}catch(Exception e){throw new BusinessException(ErrorCode.HYPOTHESIS_NOT_FOUND);}}
    private SelectionView view(ConceptPortfolioSelection value){return view(value,concept(value).getConceptName(),value.getActiveTaskRunId());}
    private SelectionView view(ConceptPortfolioSelection value,String name,String task){List<ConceptPortfolioHypothesisDecision> list=latest(value.getId()); int confirmed=(int)list.stream().filter(ConceptPortfolioHypothesisDecision::ready).count(); boolean delta=list.stream().anyMatch(ConceptPortfolioHypothesisDecision::isDeltaLegalRequired);
        return new SelectionView(value.getId(),value.getRunId(),value.getConceptId(),name,value.getStatus().name(),list.size(),confirmed,delta,
            value.getStatus()==ConceptPortfolioSelectionStatus.DELTA_LEGAL_FAILED?"FAILED":delta?"REQUIRED":"NOT_REQUIRED",
            reports.findBySelectionIdAndStatusAndDeletedAtIsNull(value.getId(),"CURRENT").isPresent()?"CURRENT":"NOT_READY",
            marketSeeds.findByPortfolioSelectionIdAndStaleAtIsNullAndDeletedAtIsNull(value.getId()).isPresent()?"READY":"NOT_READY",task,
            task != null ? "WAIT" : next(value.getStatus()),utc(value.getUpdatedAt()));}
    private String next(ConceptPortfolioSelectionStatus s){return switch(s){case HYPOTHESES_PREPARING,DELTA_LEGAL_PENDING,MARKET_SEED_FINALIZING->"WAIT";case PENDING_HYPOTHESIS_CONFIRMATION->"CONFIRM_VALIDATION_ASSUMPTIONS";case DELTA_LEGAL_FAILED->"REVISE_OR_RETRY";case READY_FOR_LEGAL_REPORT->"REVIEW_LEGAL_REPORT";case LEGAL_REPORT_READY->"FINALIZE_MARKET_SEED";case READY_FOR_MARKET->"START_MARKET_ANALYSIS";default->"NONE";};}
    private HypothesisView hypothesisView(ConceptPortfolioHypothesisDecision v) {
        JsonNode proposed = mapper.readTree(v.getProposedValueJson());
        JsonNode finalValue = v.getFinalValueJson() == null ? null : mapper.readTree(v.getFinalValueJson());
        JsonNode current = finalValue == null || finalValue.isNull() ? proposed : finalValue;
        boolean hasCurrentValue = hasValue(current);
        String blockingReason = null;
        if (!hasCurrentValue) blockingReason = "현재 값이 비어 있습니다.";
        else if (!"VALID".equals(v.getSemanticStatus())) blockingReason = v.getSemanticReason() == null
            || v.getSemanticReason().isBlank()
                ? "시장 분석 기준으로 확정하려면 값을 조금 더 구체화해 주세요."
                : v.getSemanticReason();
        else if ("FAILED".equals(v.getLegalReviewStatus()))
            blockingReason = "법률·규제 검토를 통과하지 못해 현재 값으로 확정할 수 없습니다.";
        return new HypothesisView(v.getId(), v.getHypothesisType().name(), proposed, finalValue,
            v.getSource(), v.getDecisionStatus(), v.getProposalVersion(), v.isLocked(),
            v.getSemanticStatus(), v.getSemanticReason(), v.getLegalImpact(), v.getLegalReviewStatus(),
            v.isDeltaLegalRequired(), v.getDecidedAt(), hasCurrentValue, blockingReason == null,
            blockingReason);
    }

    private boolean hasValue(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) return false;
        if (value.isTextual()) return !value.asText().isBlank();
        if (value.isArray()) {
            for (JsonNode item : value) if (hasValue(item)) return true;
            return false;
        }
        if (value.isObject()) {
            var fields = value.iterator();
            while (fields.hasNext()) if (hasValue(fields.next())) return true;
            return false;
        }
        return true;
    }
    private LegalReportView reportView(ConceptLegalRegulatoryReport v){return new LegalReportView(v.getId(),v.getSelectionId(),v.getConceptId(),v.getStatus(),v.getSchemaVersion(),v.getReportHash(),v.getBasisDate(),v.getCreatedAt(),mapper.readTree(v.getReportJson()));}
    private void copyLegal(ObjectNode out,JsonNode legal,String...names){for(String n:names)out.set(n,legal.has(n)?legal.path(n).deepCopy():mapper.createArrayNode());}
    private void requireOwned(Long owner,Long project){projects.findByIdAndOwnerIdAndDeletedAtIsNull(project,owner).orElseThrow(()->new BusinessException(ErrorCode.PROJECT_NOT_FOUND));}
    private void requireOwnedForUpdate(Long owner,Long project){projects.findByIdForUpdate(project).filter(v->v.getOwner().getId().equals(owner)).orElseThrow(()->new BusinessException(ErrorCode.PROJECT_NOT_FOUND));}
    private ActionAccepted activeReplay(Long owner, Long project, ConceptPortfolioSelection selection,
            String action, String key) {
        if (selection.getActiveTaskRunId() == null) return null;
        TaskRun task = taskRuns.getOwned(owner, project, selection.getActiveTaskRunId());
        if (action.equals(selection.getActiveAction()) && key.equals(task.getIdempotencyKey()))
            return new ActionAccepted(selection.getId(), action, task.getId(), selection.getStatus().name());
        throw new BusinessException(ErrorCode.ANALYSIS_ALREADY_RUNNING);
    }
    private Instant utc(java.time.LocalDateTime v){return v==null?null:v.toInstant(ZoneOffset.UTC);}
}
