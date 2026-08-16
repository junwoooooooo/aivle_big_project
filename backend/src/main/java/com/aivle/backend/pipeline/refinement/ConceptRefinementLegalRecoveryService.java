package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.pipeline.conceptportfolio.application.ConceptPortfolioJsonHasher;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.ConceptPortfolioSelectionService;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioHypothesisDecision;
import com.aivle.backend.pipeline.conceptportfolio.selection.domain.ConceptPortfolioSelection;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptLegalRegulatoryReportRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioHypothesisDecisionRepository;
import com.aivle.backend.pipeline.conceptportfolio.selection.repository.ConceptPortfolioSelectionRepository;
import com.aivle.backend.pipeline.market.BmPlanPreparationService;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Explicit, deterministic rollback for a legally blocked local refinement application. */
@Service
public class ConceptRefinementLegalRecoveryService {
    private final ProjectRepository projects;
    private final ConceptRefinementRoundRepository rounds;
    private final ConceptRefinementLineageGuard lineage;
    private final ConceptRefinementApplicationBeforeContract applicationBefore;
    private final ConceptRefinementDecisionContract decisions;
    private final ConceptPortfolioSelectionRepository selections;
    private final ConceptPortfolioHypothesisDecisionRepository hypotheses;
    private final ConceptLegalRegulatoryReportRepository reports;
    private final BmPlanPreparationService bmPlans;
    private final ConceptPortfolioSelectionService selectionService;
    private final ConceptRefinementService refinement;
    private final ConceptPortfolioJsonHasher hasher;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ConceptRefinementLegalRecoveryService(ProjectRepository projects,
            ConceptRefinementRoundRepository rounds, ConceptRefinementLineageGuard lineage,
            ConceptRefinementApplicationBeforeContract applicationBefore,
            ConceptRefinementDecisionContract decisions, ConceptPortfolioSelectionRepository selections,
            ConceptPortfolioHypothesisDecisionRepository hypotheses,
            ConceptLegalRegulatoryReportRepository reports, BmPlanPreparationService bmPlans,
            ConceptPortfolioSelectionService selectionService, ConceptRefinementService refinement,
            ConceptPortfolioJsonHasher hasher, ObjectMapper mapper, Clock clock) {
        this.projects=projects;this.rounds=rounds;this.lineage=lineage;this.applicationBefore=applicationBefore;
        this.decisions=decisions;this.selections=selections;this.hypotheses=hypotheses;this.reports=reports;
        this.bmPlans=bmPlans;this.selectionService=selectionService;this.refinement=refinement;
        this.hasher=hasher;this.mapper=mapper;this.clock=clock;
    }

    @Transactional
    public ConceptRefinementService.CurrentView recover(Long ownerId, Long projectId, String idempotencyKey,
            Integer expectedRound, String expectedDecisionHash) {
        owned(ownerId,projectId); String key=validKey(idempotencyKey); ConceptRefinementRound round=current(projectId);
        if(expectedRound==null||expectedRound!=round.getRoundNumber())throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        if(!Objects.equals(expectedDecisionHash,round.getDecisionHash())){
            if(key.equals(round.getRecoveryIdempotencyKey()))throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);}
        String identity=recoveryHash(round);
        if(round.getRecoveryIdempotencyKey()!=null){
            if(key.equals(round.getRecoveryIdempotencyKey())&&Objects.equals(identity,round.getRecoveryHash()))
                return guardedView(projectId,round);
            if(key.equals(round.getRecoveryIdempotencyKey()))throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
            throw unavailable();}
        if(round.getState()!=ConceptRefinementRound.State.LEGAL_BLOCKED)throw unavailable();
        if(!lineage.postApplyCurrent(projectId,round)){round.markStale();return refinement.view(round,true);}

        ConceptRefinementApplicationBeforeContract.Snapshot before;
        try { before=applicationBefore.validate(round); }
        catch(BusinessException invalid){round.markStale();return refinement.view(round,true);}
        if(!reports.findAllBySelectionIdAndStatusAndDeletedAtIsNull(round.getSelectionId(),"CURRENT").isEmpty()){
            round.markStale();return refinement.view(round,true);}

        ConceptPortfolioSelection selection=selections.findLocked(round.getSelectionId())
            .filter(value->value.isCurrent()&&value.getProjectId().equals(projectId)
                &&Objects.equals(value.getHypothesisRevision(),round.getAppliedSelectionRevision()))
            .orElseThrow(()->new BusinessException(ErrorCode.MODULE_INPUT_STALE));
        List<ConceptPortfolioHypothesisDecision> restored=new ArrayList<>();
        for(ConceptRefinementApplicationBeforeContract.BeforeHypothesis value:before.hypotheses()){
            ConceptPortfolioHypothesisDecision latest=hypotheses
                .findFirstBySelectionIdAndHypothesisTypeAndDeletedAtIsNullOrderByProposalVersionDesc(
                    round.getSelectionId(),value.type()).orElseThrow(()->new BusinessException(ErrorCode.MODULE_INPUT_STALE));
            if(!latest.getProjectId().equals(projectId)||!latest.getConceptId().equals(value.conceptId())
                    ||latest.getProposalVersion()<value.proposalVersion())
                throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
            restored.add(ConceptPortfolioHypothesisDecision.create(round.getSelectionId(),projectId,value.conceptId(),
                value.type(),value.proposedValueJson(),value.finalValueJson(),value.source(),value.decisionStatus(),
                latest.getProposalVersion()+1,value.locked(),value.semanticStatus(),value.semanticReason(),
                value.legalImpact(),value.legalReviewStatus(),value.deltaLegalRequired(),
                value.decidedByUserId(),value.decidedAt()));
        }
        hypotheses.saveAll(restored);
        selection.recoverBlockedRefinement();

        ObjectNode bmRollback=decisions.rollbackBmPatch(round);
        BmPlanPreparationService.PlanView bm=bmRollback.isEmpty()
            ? bmPlans.current(projectId)
            : bmPlans.patchForRefinement(projectId,ownerId,round.getAppliedBmPlanRevision(),bmRollback);
        if(bmRollback.isEmpty()&&bm.revision()!=round.getAppliedBmPlanRevision())
            throw new BusinessException(ErrorCode.MODULE_INPUT_STALE);
        selectionService.finalizeReport(ownerId,projectId,round.getSelectionId());
        round.recovered(selection.getHypothesisRevision(),bm.revision(),key,identity,Instant.now(clock));
        return refinement.view(round,false);
    }

    private ConceptRefinementService.CurrentView guardedView(Long projectId,ConceptRefinementRound round){
        boolean current=lineage.postApplyCurrent(projectId,round);if(!current)round.markStale();return refinement.view(round,!current);}
    private String recoveryHash(ConceptRefinementRound round){ObjectNode value=mapper.createObjectNode();
        value.put("contract","concept-refinement-legal-recovery-v1");value.put("roundId",round.getId());
        value.put("decisionHash",round.getDecisionHash());value.put("applicationHash",round.getApplicationHash());
        value.put("applicationBeforeHash",round.getApplicationBeforeHash());return hasher.hash(value);}
    private ConceptRefinementRound current(Long projectId){return rounds.findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
        .orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
    private void owned(Long ownerId,Long projectId){projects.findByIdForUpdate(projectId)
        .filter(value->value.getOwner().getId().equals(ownerId)).orElseThrow(()->new BusinessException(ErrorCode.PROJECT_NOT_FOUND));}
    private String validKey(String value){if(value==null||value.isBlank()||value.strip().length()>128)
        throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);return value.strip();}
    private BusinessException unavailable(){return new BusinessException(ErrorCode.INVALID_REQUEST,"legal recovery unavailable");}
}
