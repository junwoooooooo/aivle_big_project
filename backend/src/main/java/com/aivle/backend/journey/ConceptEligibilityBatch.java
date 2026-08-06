package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name="concept_eligibility_batches") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ConceptEligibilityBatch extends BaseEntity {
    public enum State { GENERATING, VALIDATING_ORIGIN, VALIDATING_LEGAL, COMPLETED, NEEDS_INPUT, FAILED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id",nullable=false) private Project project;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="idea_origin_version_id",nullable=false) private IdeaOriginVersion ideaOriginVersion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="legal_guardrail_set_id",nullable=false) private LegalGuardrailSet legalGuardrailSet;
    @Column(nullable=false,length=71) private String inputSnapshotHash;
    @Column(nullable=false,length=40) private String promptVersion;
    @Column(nullable=false,length=20) private String schemaVersion;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private State state;
    @Column(nullable=false) private int currentRound; @Column(nullable=false) private int inspectedCandidates;
    @Column(nullable=false) private int eligibleCandidates; @Column(nullable=false) private int targetEligibleCount;
    @Column(nullable=false) private int maxReplacementRounds; @Column(nullable=false) private int maxInspectedCandidates;
    @Column(nullable=false,columnDefinition="TEXT") private String needsInputJson;
    @Column(length=100) private String errorCode; @Column(nullable=false) private boolean retryable;
    private LocalDateTime completedAt;
    public static ConceptEligibilityBatch create(Project p,IdeaOriginVersion o,LegalGuardrailSet g,String hash,String prompt,String schema,int target,int rounds,int max){ConceptEligibilityBatch b=new ConceptEligibilityBatch();b.project=p;b.ideaOriginVersion=o;b.legalGuardrailSet=g;b.inputSnapshotHash=hash;b.promptVersion=prompt;b.schemaVersion=schema;b.state=State.GENERATING;b.targetEligibleCount=target;b.maxReplacementRounds=rounds;b.maxInspectedCandidates=max;b.needsInputJson="[]";return b;}
    public void stage(State state,int round){this.state=state;this.currentRound=round;}
    public void inspected(boolean eligible){inspectedCandidates++;if(eligible)eligibleCandidates++;}
    public void complete(){state=State.COMPLETED;retryable=false;completedAt=LocalDateTime.now();}
    public void needsInput(String json){state=State.NEEDS_INPUT;retryable=false;needsInputJson=json;completedAt=LocalDateTime.now();}
    public void fail(String code,boolean canRetry){state=State.FAILED;errorCode=code;retryable=canRetry;completedAt=LocalDateTime.now();}
    public boolean allowsManualRestart(){return state==State.FAILED&&(retryable||"AI_CONFIGURATION_INVALID".equals(errorCode)||"AI_RESULT_INVALID".equals(errorCode));}
}
