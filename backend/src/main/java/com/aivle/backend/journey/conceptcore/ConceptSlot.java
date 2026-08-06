package com.aivle.backend.journey.conceptcore;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name="concept_slots") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ConceptSlot extends BaseEntity {
    public enum Status { QUEUED,GENERATING,GENERATED,SCHEMA_INVALID,TRANSIENT_PROVIDER_FAILURE,PERMANENT_PROVIDER_FAILURE,VALIDATING_ORIGIN,VALIDATING_BOUNDARY,REDESIGNING,REPLACING,ELIGIBLE,REJECTED,NEEDS_INPUT,FAILED,STALE }
    public enum Phase { INITIAL,REPAIR,REDESIGN,REPLACEMENT }
    public enum Focus { TARGET_AND_USER_EXPERIENCE,OPERATING_MODEL_AND_PARTNERS,REVENUE_AND_CHANNELS }
    public enum LegalState { IMPLEMENTABLE,IMPLEMENTABLE_WITH_CONTROLS,REDESIGN_REQUIRED,INSUFFICIENT_INFORMATION,HARD_BLOCK }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="batch_id",nullable=false) private ConceptExplorationBatch batch;
    @Column(nullable=false) private int slotIndex;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=50) private Focus variationFocus;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private Status status;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Phase currentPhase;
    @Column(nullable=false) private int attemptCount;
    @Enumerated(EnumType.STRING) @Column(length=40) private LegalState legalState;
    @Column(nullable=false) private boolean eligible;
    @Column(nullable=false,length=120) private String safeMessageKey;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="replacement_for_slot_id") private ConceptSlot replacementForSlot;
    public static ConceptSlot create(ConceptExplorationBatch batch,int index,Focus focus,Phase phase){ConceptSlot value=new ConceptSlot();value.batch=batch;value.slotIndex=index;value.variationFocus=focus;value.currentPhase=phase;value.status=Status.QUEUED;value.safeMessageKey="job.concept.slot.queued";return value;}
    public void generated(Phase phase,int attempts){currentPhase=phase;attemptCount=attempts;status=Status.GENERATED;safeMessageKey="job.concept.slot.generated";}
    public void failed(Status failure,Phase phase,int attempts){if(!java.util.Set.of(Status.SCHEMA_INVALID,Status.TRANSIENT_PROVIDER_FAILURE,Status.PERMANENT_PROVIDER_FAILURE,Status.FAILED).contains(failure))throw new IllegalArgumentException();status=failure;currentPhase=phase;attemptCount=attempts;safeMessageKey="job.concept.slot.rejected";}
    public void validatingOrigin(){status=Status.VALIDATING_ORIGIN;safeMessageKey="job.concept.slot.validating_origin";}
    public void validatingBoundary(){status=Status.VALIDATING_BOUNDARY;safeMessageKey="job.concept.slot.validating_boundary";}
    public void eligible(LegalState state){if(state!=LegalState.IMPLEMENTABLE&&state!=LegalState.IMPLEMENTABLE_WITH_CONTROLS)throw new IllegalArgumentException();legalState=state;eligible=true;status=Status.ELIGIBLE;safeMessageKey="job.concept.slot.eligible";}
    public void reject(LegalState state){legalState=state;eligible=false;status=state==LegalState.INSUFFICIENT_INFORMATION?Status.NEEDS_INPUT:Status.REJECTED;safeMessageKey="job.concept.slot.rejected";}
    public void stale(){eligible=false;status=Status.STALE;safeMessageKey="job.concept.slot.stale";}
}
