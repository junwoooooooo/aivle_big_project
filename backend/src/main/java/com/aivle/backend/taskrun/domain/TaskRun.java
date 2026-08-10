package com.aivle.backend.taskrun.domain;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name="task_runs") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class TaskRun extends BaseEntity {
 @Id @Column(length=64) private String id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id",nullable=false) private Project project;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=50) private TaskType taskType;
 @Column(nullable=false,length=80) private String subjectType;
 @Column(nullable=false,length=64) private String subjectId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private TaskRunState state;
 @Column(name="input_snapshot_json",nullable=false,columnDefinition="TEXT") private String inputSnapshot;
 @Column(name="canonical_input_hash",nullable=false,length=71) private String inputHash;
 @Column(nullable=false,length=128) private String idempotencyKey;
 @Column(nullable=false,length=200) private String idempotencyScope;
 @Column(length=128) private String lastRetryIdempotencyKey;
 @Column(nullable=false,length=128) private String correlationId;
 @Column(nullable=false,length=20) private String contractVersion;
 @Column(nullable=false,length=20) private String taskSchemaVersion;
 @Column(nullable=false,length=20) private String locale;
 @Column(nullable=false) private boolean retryable;
 @Column(nullable=false) private boolean cancelRequested;
 @Column(nullable=false) private int maxAttempts;
 @Column(nullable=false) private int attemptCount;
 @Column(length=64) private String currentAttemptId;
 @Column(length=64) private String finalResultId;
 @Column(length=80) private String lastErrorCode;
 @Column(length=100) private String lastErrorReason;
 private LocalDateTime startedAt;
 private LocalDateTime finishedAt;
 private LocalDateTime nextAttemptAt;
 public static TaskRun create(Project project,TaskType type,String subjectType,String subjectId,String input,String hash,String key,String correlation,int max){TaskRun r=new TaskRun();r.id=UUID.randomUUID().toString();r.project=project;r.taskType=type;r.subjectType=subjectType;r.subjectId=subjectId;r.inputSnapshot=input;r.inputHash=hash;r.idempotencyKey=key;r.idempotencyScope=type.name()+":"+subjectType+":"+subjectId;r.correlationId=correlation;r.contractVersion="1.0";r.taskSchemaVersion="1.0";r.locale="ko-KR";r.state=TaskRunState.QUEUED;r.maxAttempts=max;r.nextAttemptAt=LocalDateTime.now();return r;}
 public int nextAttemptNumber(){requireClaimable();return ++attemptCount;}
 public void scheduleInitial(LocalDateTime now){if(state!=TaskRunState.QUEUED||attemptCount!=0)throw new IllegalStateException("task already scheduled");nextAttemptAt=now;}
 public void registerAttempt(String attemptId){requireClaimable();currentAttemptId=attemptId;}
 public void claimed(String attemptId,LocalDateTime now){requireClaimable();currentAttemptId=attemptId;state=TaskRunState.RUNNING;retryable=false;nextAttemptAt=now;if(startedAt==null)startedAt=now;}
 public void succeed(String resultId,LocalDateTime now){requireRunning();finalResultId=resultId;state=TaskRunState.SUCCEEDED;retryable=false;finishedAt=now;lastErrorCode=null;lastErrorReason=null;}
 public void needsInput(LocalDateTime now){needsInput(null,now);}
 public void needsInput(String resultId,LocalDateTime now){requireRunning();finalResultId=resultId;state=TaskRunState.NEEDS_INPUT;retryable=false;finishedAt=now;lastErrorCode="NEEDS_INPUT";}
 public void fail(String code,String reason,boolean canRetry,LocalDateTime now){requireRunning();state=TaskRunState.FAILED;retryable=canRetry&&attemptCount<maxAttempts;lastErrorCode=code;lastErrorReason=reason;finishedAt=now;}
 public void timeOut(LocalDateTime now){requireRunning();state=TaskRunState.TIMED_OUT;retryable=attemptCount<maxAttempts;lastErrorCode="TASK_TIMEOUT";finishedAt=now;}
 public void recoverAfterLeaseExpiry(LocalDateTime now){requireRunning();lastErrorCode="TASK_TIMEOUT";if(attemptCount<maxAttempts){state=TaskRunState.QUEUED;retryable=false;finishedAt=null;nextAttemptAt=now;}else{state=TaskRunState.TIMED_OUT;retryable=false;finishedAt=now;}}
 public void exhaustAttempts(LocalDateTime now){if(state!=TaskRunState.QUEUED&&state!=TaskRunState.READY)throw new IllegalStateException("task run is not claimable");state=TaskRunState.FAILED;retryable=false;lastErrorCode="ATTEMPT_LIMIT_EXCEEDED";finishedAt=now;}
 public void cancel(LocalDateTime now){if(terminal())return;cancelRequested=true;if(state==TaskRunState.QUEUED||state==TaskRunState.READY||state==TaskRunState.RUNNING){state=TaskRunState.CANCELLED;retryable=false;finishedAt=now;}}
 public boolean terminal(){return state==TaskRunState.SUCCEEDED||state==TaskRunState.NEEDS_INPUT||state==TaskRunState.FAILED||state==TaskRunState.CANCELLED||state==TaskRunState.TIMED_OUT;}
 private void requireClaimable(){if(state!=TaskRunState.QUEUED&&state!=TaskRunState.READY)throw new IllegalStateException("task run is not claimable");}
 private void requireRunning(){if(state!=TaskRunState.RUNNING)throw new IllegalStateException("task run is not running");}
}
