package com.aivle.backend.pipeline.conceptportfolio.selection.worker;

import com.aivle.backend.jobevent.*;
import com.aivle.backend.pipeline.conceptportfolio.selection.application.*;
import com.aivle.backend.pipeline.conceptportfolio.worker.ConceptPortfolioExecutionProperties;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.*;
import com.aivle.backend.taskrun.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConceptPortfolioSelectionWorker {
    private static final TaskType TYPE=TaskType.CONCEPT_PORTFOLIO_V2_SELECTION_ACTION;
    private final TaskRunService taskRuns; private final InternalAiExecutionClient ai;
    private final ConceptPortfolioSelectionMaterializationService materialization; private final JobEventPublisher events;
    private final ConceptPortfolioExecutionProperties properties; private final ExecutorService executor; private final Clock clock;
    private final String workerId="concept-portfolio-selection-"+UUID.randomUUID();
    public ConceptPortfolioSelectionWorker(TaskRunService taskRuns,InternalAiExecutionClient ai,
            ConceptPortfolioSelectionMaterializationService materialization,JobEventPublisher events,
            ConceptPortfolioExecutionProperties properties,@Qualifier("conceptPortfolioAiExecutor") ExecutorService executor,Clock clock){
        this.taskRuns=taskRuns;this.ai=ai;this.materialization=materialization;this.events=events;this.properties=properties;this.executor=executor;this.clock=clock;}
    @Scheduled(fixedDelayString="${app.task-run.concept-portfolio.selection-poll-interval-ms:1000}") public void poll(){processOne();}
    @Scheduled(fixedDelayString="${app.task-run.concept-portfolio.selection-recovery-interval-ms:5000}") public void recover(){for(String id:taskRuns.recoverExpiredTaskIds(Duration.ZERO,List.of(TYPE)))publish(taskRuns.workerContext(id),"QUEUED",JobEvent.Status.QUEUED,null);}
    public boolean processOne(){TaskRunService.Claim claim=taskRuns.claimNext(TYPE,workerId,properties.lease(),properties.taskTimeout());if(claim==null)return false;
        TaskRunWorkerContext context=taskRuns.workerContext(claim.taskRunId());Future<ExecutionResponse> future=null;try{taskRuns.startExecution(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken());publish(context,"RUNNING",JobEvent.Status.RUNNING,null);
            future=executor.submit(()->ai.executeWorker(context,claim.taskAttemptId(),LocalDateTime.now(clock).plus(properties.aiDeadline())));ExecutionResponse response=await(claim,future);if(response==null)return true;
            publish(context,"MATERIALIZING",JobEvent.Status.RUNNING,null);materialization.complete(claim,context,response);publish(context,"COMPLETED",JobEvent.Status.COMPLETED,null);
        }catch(ExecutionFailure f){fail(claim,context,f.code(),f.reason(),f.retryable());}catch(ConceptPortfolioSelectionMaterializationService.ContractViolation f){fail(claim,context,"RESULT_SCHEMA_INVALID","AI_RESULT_INVALID",false);}
        catch(TaskRunFailure stale){if(!Set.of("STALE_CLAIM","LATE_OR_DUPLICATE_RESULT").contains(stale.getReason()))fail(claim,context,stale.getCode(),stale.getReason(),stale.isRetryable());}
        catch(RejectedExecutionException f){fail(claim,context,"EXECUTION_FAILED","TRANSIENT_EXECUTION_FAILURE",true);}catch(RuntimeException f){fail(claim,context,"RESULT_SCHEMA_INVALID","AI_RESULT_INVALID",false);}finally{if(future!=null&&!future.isDone())future.cancel(true);}return true;}
    private ExecutionResponse await(TaskRunService.Claim claim,Future<ExecutionResponse> future){while(true)try{return future.get(properties.heartbeatInterval().toMillis(),TimeUnit.MILLISECONDS);}catch(TimeoutException e){try{taskRuns.heartbeat(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken(),properties.lease());}catch(RuntimeException lost){future.cancel(true);return null;}}catch(InterruptedException e){Thread.currentThread().interrupt();future.cancel(true);return null;}catch(ExecutionException e){if(e.getCause() instanceof ExecutionFailure f)throw f;if(e.getCause() instanceof RuntimeException r)throw r;throw new IllegalStateException(e.getCause());}}
    private void fail(TaskRunService.Claim c,TaskRunWorkerContext x,String code,String reason,boolean retryable){try{materialization.fail(c,x,code,reason,retryable);publish(x,"FAILED",JobEvent.Status.FAILED,"RESULT_SCHEMA_INVALID".equals(code)?"AI_RESULT_INVALID":"AI_SERVICE_UNAVAILABLE");}catch(RuntimeException ignored){}}
    private void publish(TaskRunWorkerContext c,String stage,JobEvent.Status status,String code){String key="job.concept-portfolio.selection."+stage.toLowerCase();events.publish(new JobEventPublisher.Command(c.projectId(),c.taskRunId(),c.taskRunId(),stage,key,status,key,Map.of(),code));}
}
