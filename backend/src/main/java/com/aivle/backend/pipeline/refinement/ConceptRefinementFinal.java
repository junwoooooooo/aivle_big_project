package com.aivle.backend.pipeline.refinement;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "concept_refinement_finals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptRefinementFinal extends BaseEntity {
    public enum Outcome { REFINED, KEEP_CURRENT, NO_CHANGES }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="project_id",nullable=false) private Long projectId;
    @Column(name="round_id",nullable=false,unique=true) private Long roundId;
    @Column(name="selection_id",nullable=false) private Long selectionId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Outcome outcome;
    @Column(name="source_business_validation_session_id",nullable=false,length=64) private String sourceBusinessValidationSessionId;
    @Column(name="source_market_seed_snapshot_id",nullable=false,length=64) private String sourceMarketSeedSnapshotId;
    @Column(name="final_market_seed_snapshot_id",nullable=false,length=64) private String finalMarketSeedSnapshotId;
    @Column(name="source_selection_revision",nullable=false) private int sourceSelectionRevision;
    @Column(name="source_bm_plan_revision",nullable=false) private int sourceBmPlanRevision;
    @Column(name="final_selection_revision",nullable=false) private int finalSelectionRevision;
    @Column(name="final_bm_plan_revision",nullable=false) private int finalBmPlanRevision;
    @Column(name="decision_hash",length=71) private String decisionHash;
    @Column(name="application_hash",length=71) private String applicationHash;
    @Column(name="overlay_json",nullable=false,columnDefinition="TEXT") private String overlayJson;
    @Column(name="final_json",nullable=false,columnDefinition="TEXT") private String finalJson;
    @Column(name="final_hash",nullable=false,length=71) private String finalHash;
    @Column(name="finalized_by_user_id",nullable=false) private Long finalizedByUserId;
    @Column(name="finalized_at",nullable=false) private Instant finalizedAt;

    public static ConceptRefinementFinal create(Long projectId, ConceptRefinementRound round,
            Outcome outcome, String finalSeedId, int finalSelectionRevision, int finalBmRevision,
            String overlayJson, String finalJson, String finalHash, Long userId, Instant now) {
        if (projectId == null || round == null || round.getId() == null || outcome == null
                || blank(finalSeedId) || blank(overlayJson) || blank(finalJson) || !hash(finalHash)
                || userId == null || now == null) throw new IllegalArgumentException("refinement final is invalid");
        ConceptRefinementFinal value = new ConceptRefinementFinal();
        value.projectId=projectId; value.roundId=round.getId(); value.selectionId=round.getSelectionId();
        value.outcome=outcome; value.sourceBusinessValidationSessionId=round.getBusinessValidationSessionId();
        value.sourceMarketSeedSnapshotId=round.getSourceMarketSeedSnapshotId();
        value.finalMarketSeedSnapshotId=finalSeedId; value.sourceSelectionRevision=round.getSourceSelectionRevision();
        value.sourceBmPlanRevision=round.getSourceBmPlanRevision(); value.finalSelectionRevision=finalSelectionRevision;
        value.finalBmPlanRevision=finalBmRevision; value.decisionHash=round.getDecisionHash();
        value.applicationHash=round.getApplicationHash(); value.overlayJson=overlayJson;
        value.finalJson=finalJson; value.finalHash=finalHash; value.finalizedByUserId=userId; value.finalizedAt=now;
        return value;
    }
    private static boolean blank(String v){return v==null||v.isBlank();}
    private static boolean hash(String v){return v!=null&&v.matches("sha256:[0-9a-f]{64}");}
}
