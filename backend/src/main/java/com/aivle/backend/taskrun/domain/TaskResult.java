package com.aivle.backend.taskrun.domain;
import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;
@Entity @Table(name="task_results") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class TaskResult extends BaseEntity {
 @Id @Column(length=64) private String id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="task_run_id",nullable=false) private TaskRun taskRun;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="task_attempt_id",nullable=false) private TaskAttempt taskAttempt;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private TaskResultValidationState validationState;
 @Column(nullable=false,length=20) private String contractVersion; @Column(nullable=false,length=20) private String taskSchemaVersion; @Column(nullable=false,length=20) private String resultSchemaVersion;
 @Column(nullable=false,columnDefinition="TEXT") private String resultJson; @Column(nullable=false,length=71) private String resultHash;
 @Column(length=100) private String rejectionCode; @Column(nullable=false) private java.time.LocalDateTime receivedAt;
 private java.time.LocalDateTime validatedAt; private java.time.LocalDateTime adoptedAt; private java.time.LocalDateTime rejectedAt;
 public static TaskResult received(TaskRun run,TaskAttempt attempt,String payload,String resultHash,String schema,java.time.LocalDateTime now){TaskResult r=new TaskResult();r.id=UUID.randomUUID().toString();r.taskRun=run;r.taskAttempt=attempt;r.validationState=TaskResultValidationState.RECEIVED;r.contractVersion="1.0";r.taskSchemaVersion=run.getTaskSchemaVersion();r.resultSchemaVersion=schema;r.resultJson=payload;r.resultHash=resultHash;r.receivedAt=now;return r;}
 public void validateResult(java.time.LocalDateTime now){if(validationState!=TaskResultValidationState.RECEIVED)throw new IllegalStateException("result is not received");validationState=TaskResultValidationState.VALIDATED;validatedAt=now;}
 public void adopt(java.time.LocalDateTime now){if(validationState!=TaskResultValidationState.VALIDATED)throw new IllegalStateException("result is not validated");validationState=TaskResultValidationState.ADOPTED;adoptedAt=now;}
 public void reject(String reason,java.time.LocalDateTime now){if(validationState==TaskResultValidationState.ADOPTED)throw new IllegalStateException("adopted result is immutable");validationState=TaskResultValidationState.REJECTED;rejectionCode=reason;rejectedAt=now;}
 public static TaskResult adopted(TaskRun run,TaskAttempt attempt,String payload,String resultHash,String schema,java.time.LocalDateTime now){TaskResult r=received(run,attempt,payload,resultHash,schema,now);r.validateResult(now);r.adopt(now);return r;}
 public static TaskResult rejected(TaskRun run,TaskAttempt attempt,String payload,String resultHash,String schema,String reason,java.time.LocalDateTime now){TaskResult r=received(run,attempt,payload,resultHash,schema,now);r.reject(reason,now);return r;}
}
