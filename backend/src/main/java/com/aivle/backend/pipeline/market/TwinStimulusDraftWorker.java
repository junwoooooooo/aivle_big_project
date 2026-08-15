package com.aivle.backend.pipeline.market;

import com.aivle.backend.jobevent.JobEvent;
import com.aivle.backend.jobevent.JobEventPublisher;
import com.aivle.backend.taskrun.contract.TwinStimulusDraftContract;
import com.aivle.backend.taskrun.domain.TaskType;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient;
import com.aivle.backend.taskrun.integration.InternalAiExecutionClient.ExecutionFailure;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.taskrun.service.TaskRunWorkerContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class TwinStimulusDraftWorker {
    private static final TaskType TYPE=TaskType.TWIN_STIMULUS_DRAFT;
    private static final Duration BUDGET=Duration.ofSeconds(90);
    private final TaskRunService tasks;
    private final InternalAiExecutionClient ai;
    private final JobEventPublisher events;
    private final ObjectMapper mapper;
    private final String workerId="twin-stimulus-"+UUID.randomUUID();
    public TwinStimulusDraftWorker(TaskRunService tasks,InternalAiExecutionClient ai,
            JobEventPublisher events,ObjectMapper mapper){
        this.tasks=tasks;this.ai=ai;this.events=events;this.mapper=mapper;
    }
    @Scheduled(fixedDelayString="${app.task-run.twin-stimulus-poll-interval-ms:1500}")
    public void poll(){processOne();}
    public boolean processOne(){
        var claim=tasks.claimNext(TYPE,workerId,BUDGET.plusMinutes(2),BUDGET);
        if(claim==null)return false;
        TaskRunWorkerContext context=tasks.workerContext(claim.taskRunId());
        tasks.startExecution(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken());
        publish(context,"PREPARING","job.twin.stimulus.preparing",JobEvent.Status.RUNNING,null);
        try{
            var response=ai.executeWorker(context,claim.taskAttemptId(),LocalDateTime.now().plus(BUDGET));
            TwinStimulusDraftContract.validate(response.result());
            tasks.adopt(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken(),
                mapper.writeValueAsString(response.result()),response.canonicalInputHash(),
                response.resultSchemaVersion());
            publish(context,"COMPLETED","job.twin.stimulus.completed",JobEvent.Status.COMPLETED,null);
        }catch(ExecutionFailure failure){
            tasks.fail(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken(),
                failure.code(),failure.reason(),failure.retryable());
            publish(context,"FAILED","job.twin.stimulus.failed",JobEvent.Status.FAILED,
                "TWIN_STIMULUS_NO_SERVICEABLE_PAIR".equals(failure.reason())
                    ? failure.reason():"AI_SERVICE_UNAVAILABLE");
        }catch(RuntimeException failure){
            tasks.fail(claim.taskRunId(),claim.taskAttemptId(),claim.claimToken(),
                "RESULT_SCHEMA_INVALID","AI_RESULT_INVALID",false);
            publish(context,"FAILED","job.twin.stimulus.failed",JobEvent.Status.FAILED,"AI_RESULT_INVALID");
        }
        return true;
    }
    private void publish(TaskRunWorkerContext context,String stage,String key,JobEvent.Status status,String code){
        events.publish(new JobEventPublisher.Command(context.projectId(),context.taskRunId(),
            context.taskRunId(),stage,key,status,key,Map.of(),code));
    }
}
