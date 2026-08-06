package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.taskrun.domain.TaskRun;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name="concept_drafts") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ConceptDraft extends BaseEntity {
    public enum OriginStatus { PASS, FAIL_ORIGIN } public enum LegalStatus { PASS, FAIL_LEGAL }
    public enum EligibilityStatus { PENDING, ELIGIBLE, REJECTED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="batch_id",nullable=false) private ConceptEligibilityBatch batch;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="generation_task_run_id",nullable=false) private TaskRun generationTaskRun;
    @Column(nullable=false) private int roundNumber; @Column(nullable=false) private int sequenceNumber;
    @Column(nullable=false,length=71) private String inputSnapshotHash; @Column(nullable=false,length=40) private String promptVersion;
    @Column(nullable=false,length=20) private String schemaVersion; @Column(nullable=false,length=71) private String fingerprint;
    @Column(nullable=false,columnDefinition="TEXT") private String draftJson;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private OriginStatus originStatus;
    @Column(nullable=false,columnDefinition="TEXT") private String originReasonsJson;
    @Enumerated(EnumType.STRING) @Column(length=20) private LegalStatus legalStatus;
    @Column(nullable=false,columnDefinition="TEXT") private String legalReasonsJson;
    @Column(nullable=false,columnDefinition="TEXT") private String violatedStructureKeysJson;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EligibilityStatus eligibilityStatus;
    @Column(columnDefinition="TEXT") private String userComment;
    public static ConceptDraft create(ConceptEligibilityBatch b,TaskRun task,int round,int sequence,String hash,String prompt,String schema,String fingerprint,String json,OriginStatus status,String reasons){ConceptDraft d=new ConceptDraft();d.batch=b;d.generationTaskRun=task;d.roundNumber=round;d.sequenceNumber=sequence;d.inputSnapshotHash=hash;d.promptVersion=prompt;d.schemaVersion=schema;d.fingerprint=fingerprint;d.draftJson=json;d.originStatus=status;d.originReasonsJson=reasons;d.legalReasonsJson="[]";d.violatedStructureKeysJson="[]";d.eligibilityStatus=status==OriginStatus.PASS?EligibilityStatus.PENDING:EligibilityStatus.REJECTED;return d;}
    public void legal(LegalStatus status,String reasons,String keys,String comment,String validatedDraftJson){legalStatus=status;legalReasonsJson=reasons;violatedStructureKeysJson=keys;userComment=comment;draftJson=validatedDraftJson;eligibilityStatus=status==LegalStatus.PASS?EligibilityStatus.ELIGIBLE:EligibilityStatus.REJECTED;}
}
