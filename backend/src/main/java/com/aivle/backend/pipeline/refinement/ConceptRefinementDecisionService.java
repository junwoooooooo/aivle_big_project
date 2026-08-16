package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.common.exception.ErrorCode;
import com.aivle.backend.project.repository.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records a human decision only; it never mutates product state or creates a TaskRun. */
@Service
public class ConceptRefinementDecisionService {
    private final ProjectRepository projects;
    private final ConceptRefinementLineageGuard lineage;
    private final ConceptRefinementRoundRepository rounds;
    private final ConceptRefinementDecisionContract contract;
    private final ConceptRefinementService refinement;

    public ConceptRefinementDecisionService(ProjectRepository projects,
            ConceptRefinementLineageGuard lineage,
            ConceptRefinementRoundRepository rounds, ConceptRefinementDecisionContract contract,
            ConceptRefinementService refinement) {
        this.projects = projects; this.lineage = lineage; this.rounds = rounds;
        this.contract = contract; this.refinement = refinement;
    }

    @Transactional
    public ConceptRefinementService.CurrentView decide(Long ownerId, Long projectId,
            String idempotencyKey, Integer expectedRound, String expectedProposalSetHash,
            List<String> selectedProposalKeys, boolean keepCurrent) {
        ownedForUpdate(ownerId, projectId);
        String key = validKey(idempotencyKey);
        ConceptRefinementRound round = rounds
            .findTopByProjectIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (expectedRound == null || expectedRound < 1 || expectedRound != round.getRoundNumber()) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT,
                "현재 refinement round와 요청한 round가 다릅니다.");
        }
        if (expectedProposalSetHash == null
                || !expectedProposalSetHash.matches("sha256:[0-9a-f]{64}")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "proposalSetHash가 올바르지 않습니다.");
        }

        ConceptRefinementDecisionContract.ProposalSet proposalSet = contract.proposalSet(round);
        if (!Objects.equals(expectedProposalSetHash, proposalSet.hash())) {
            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT,
                "현재 proposal set과 요청한 proposal set이 다릅니다.");
        }
        ConceptRefinementDecisionContract.DecisionMaterial decision = contract.decision(
            round, proposalSet, selectedProposalKeys, keepCurrent);

        if (round.getState() == ConceptRefinementRound.State.DECISION_RECORDED
                || round.getState() == ConceptRefinementRound.State.KEEP_CURRENT) {
            if (key.equals(round.getDecisionIdempotencyKey())) {
                if (!decision.hash().equals(round.getDecisionHash())) {
                    throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
                }
                return refinement.view(round, false);
            }
            throw unavailable();
        }
        if (round.getState() != ConceptRefinementRound.State.AWAITING_DECISION) throw unavailable();
        if (!lineage.preApplyCurrent(ownerId, projectId, round)) {
            round.markStale();
            return refinement.view(round, true);
        }

        round.recordDecision(decision.snapshot().toString(), decision.hash(), key,
            ownerId, Instant.now(), keepCurrent);
        return refinement.view(round, false);
    }

    private void ownedForUpdate(Long ownerId, Long projectId) {
        projects.findByIdForUpdate(projectId)
            .filter(value -> value.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private static String validKey(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 128)
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        return value.strip();
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.INVALID_REQUEST,
            "현재 refinement 상태에서는 decision을 기록할 수 없습니다.");
    }
}
