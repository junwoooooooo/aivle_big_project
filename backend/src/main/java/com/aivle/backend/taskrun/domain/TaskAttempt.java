package com.aivle.backend.taskrun.domain;
import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
@Entity @Table(name="task_attempts") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class TaskAttempt extends BaseEntity {
 @Id @Column(length=64) private String id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="task_run_id",nullable=false) private TaskRun taskRun;
 @Column(nullable=false) private int attemptNumber;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private TaskAttemptState state;
 @Column(length=64) private String claimToken; @Column(name="claimed_by",length=128) private String workerId;
 private LocalDateTime claimedAt;
 private LocalDateTime leaseExpiresAt; private LocalDateTime heartbeatAt; @Column(nullable=false) private LocalDateTime deadlineAt;
 private LocalDateTime startedAt; private LocalDateTime finishedAt;
 @Column(name="normalized_error_code",length=80) private String errorCode; @Column(name="normalized_error_reason",length=100) private String errorReason; @Column(nullable=false) private boolean retryable;
 public static TaskAttempt pending(TaskRun run,LocalDateTime deadline){TaskAttempt a=new TaskAttempt();a.id=UUID.randomUUID().toString();a.taskRun=run;a.attemptNumber=run.nextAttemptNumber();a.state=TaskAttemptState.CREATED;a.deadlineAt=deadline;run.registerAttempt(a.id);return a;}
 public static TaskAttempt claim(TaskRun run,String worker,LocalDateTime now,LocalDateTime lease,LocalDateTime deadline){TaskAttempt a=pending(run,deadline);a.claim(worker,now,lease);return a;}
 public void claim(String worker,LocalDateTime now,LocalDateTime lease){if(state!=TaskAttemptState.CREATED)throw new IllegalStateException("attempt is not claimable");state=TaskAttemptState.CLAIMED;claimToken=UUID.randomUUID().toString();workerId=worker;claimedAt=now;leaseExpiresAt=lease;heartbeatAt=now;taskRun.claimed(id,now);}
 public void claim(String worker,LocalDateTime now,LocalDateTime lease,LocalDateTime deadline){claim(worker,now,lease);deadlineAt=deadline;}
 public void start(String token,LocalDateTime now){requireClaim(token);if(state!=TaskAttemptState.CLAIMED)throw new IllegalStateException("attempt is not claimed");state=TaskAttemptState.RUNNING;startedAt=now;}
 public void heartbeat(String token,LocalDateTime now,LocalDateTime lease){requireClaim(token);if(state!=TaskAttemptState.RUNNING)throw new IllegalStateException("attempt is not running");heartbeatAt=now;leaseExpiresAt=lease;}
 public void succeed(String token,LocalDateTime now){requireClaim(token);requireRunning();state=TaskAttemptState.SUCCEEDED;finishedAt=now;}
 public void needsInput(String token,LocalDateTime now){requireClaim(token);requireRunning();state=TaskAttemptState.NEEDS_INPUT;errorCode="NEEDS_INPUT";errorReason="ADDITIONAL_INPUT_REQUIRED";retryable=false;finishedAt=now;}
 public void fail(String token,String code,String reason,boolean canRetry,LocalDateTime now){requireClaim(token);if(state!=TaskAttemptState.CLAIMED&&state!=TaskAttemptState.RUNNING)throw new IllegalStateException("attempt is not active");state=TaskAttemptState.FAILED;errorCode=code;errorReason=reason;retryable=canRetry;finishedAt=now;}
 public void timeOut(LocalDateTime now){state=TaskAttemptState.TIMED_OUT;errorCode="DEADLINE_EXCEEDED";errorReason="REQUEST_DEADLINE_EXCEEDED";retryable=true;finishedAt=now;}
 public void cancel(LocalDateTime now){if(state==TaskAttemptState.CREATED||state==TaskAttemptState.CLAIMED||state==TaskAttemptState.RUNNING){state=TaskAttemptState.CANCELLED;retryable=false;finishedAt=now;}}
 public boolean leaseExpired(LocalDateTime now){return (state==TaskAttemptState.CLAIMED||state==TaskAttemptState.RUNNING)&&leaseExpiresAt!=null&&!leaseExpiresAt.isAfter(now);}
 public void assertCompletable(String token,LocalDateTime now){requireClaim(token);if(state!=TaskAttemptState.RUNNING||leaseExpired(now)||!deadlineAt.isAfter(now))throw new IllegalStateException("attempt claim or lease is no longer valid");}
 private void requireClaim(String token){if(claimToken==null||!claimToken.equals(token))throw new IllegalArgumentException("stale claim token");}
 private void requireRunning(){if(state!=TaskAttemptState.RUNNING)throw new IllegalStateException("attempt is not running");}
}
