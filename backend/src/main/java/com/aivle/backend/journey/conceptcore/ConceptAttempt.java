package com.aivle.backend.journey.conceptcore;
import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;import lombok.Getter;import lombok.NoArgsConstructor;
@Entity @Table(name="concept_attempts") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ConceptAttempt extends BaseEntity {
 public enum Outcome{VALID,SCHEMA_INVALID,TRANSIENT_PROVIDER_FAILURE,PERMANENT_PROVIDER_FAILURE} public enum DuplicateStatus{UNIQUE,NEAR_DUPLICATE,DUPLICATE}
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="slot_id",nullable=false) private ConceptSlot slot;
 @Column(nullable=false) private int attemptNumber; @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ConceptSlot.Phase phase;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private Outcome outcome; @Column(length=40) private String providerFailureType;
 @Column(columnDefinition="TEXT") private String candidateJson; @Column(length=71) private String conceptSnapshotHash;
 @Enumerated(EnumType.STRING) @Column(length=20) private DuplicateStatus duplicateStatus; @Column(length=71) private String duplicateKey;
 @Column(nullable=false,columnDefinition="TEXT") private String negativeConstraintJson;
 public static ConceptAttempt create(ConceptSlot slot,int number,ConceptSlot.Phase phase,Outcome outcome,String failure,String json,String hash,DuplicateStatus duplicate,String duplicateKey,String negative){ConceptAttempt value=new ConceptAttempt();value.slot=slot;value.attemptNumber=number;value.phase=phase;value.outcome=outcome;value.providerFailureType=failure;value.candidateJson=json;value.conceptSnapshotHash=hash;value.duplicateStatus=duplicate;value.duplicateKey=duplicateKey;value.negativeConstraintJson=negative==null?"{}":negative;return value;}
 public void classifyDuplicate(DuplicateStatus status,String key){if(outcome!=Outcome.VALID)throw new IllegalStateException("only valid candidate can be classified");duplicateStatus=status;duplicateKey=key;}
}
