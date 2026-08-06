package com.aivle.backend.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.aivle.backend.common.exception.BusinessException;
import com.aivle.backend.journey.boundary.*;
import com.aivle.backend.journey.brief.*;
import com.aivle.backend.journey.conceptcore.*;
import com.aivle.backend.journey.conversation.ConversationService;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.project.repository.ProjectRepository;
import com.aivle.backend.taskrun.domain.*;
import com.aivle.backend.taskrun.service.TaskRunService;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.user.repository.UserRepository;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.dao.DataIntegrityViolationException;import org.springframework.test.context.ActiveProfiles;

@Tag("postgres") @SpringBootTest(properties="spring.task.scheduling.enabled=false") @ActiveProfiles("test")
class PostgreSqlConceptCoreTests extends PostgreSqlIntegrationTestSupport {
 @Autowired UserRepository users;@Autowired ProjectRepository projects;@Autowired ConversationService conversations;@Autowired OpportunityBriefService briefs;@Autowired RegulatoryBoundaryService boundaryFoundation;@Autowired ConceptExplorationApplicationService service;@Autowired ConceptExplorationBatchRepository batches;@Autowired ConceptSlotRepository slots;@Autowired ConceptAttemptRepository attempts;@Autowired TaskRunService tasks;
 @Test void batchIsIdempotentAndSlotAttemptSequencesAreUnique(){Context c=context();var first=service.start(c.owner.getId(),c.project.getId(),c.brief.getId(),c.boundary.getId());var replay=service.start(c.owner.getId(),c.project.getId(),c.brief.getId(),c.boundary.getId());assertThat(replay.batchId()).isEqualTo(first.batchId());ConceptExplorationBatch batch=batches.findById(first.batchId()).orElseThrow();ConceptSlot slot=slots.saveAndFlush(ConceptSlot.create(batch,0,ConceptSlot.Focus.TARGET_AND_USER_EXPERIENCE,ConceptSlot.Phase.INITIAL));assertThatThrownBy(()->slots.saveAndFlush(ConceptSlot.create(batch,0,ConceptSlot.Focus.OPERATING_MODEL_AND_PARTNERS,ConceptSlot.Phase.INITIAL))).isInstanceOf(DataIntegrityViolationException.class);tasks.cancel(c.owner.getId(),c.project.getId(),first.jobId());}
 @Test void expiredLeaseRecoversAndBriefChangeMarksBatchStale(){Context c=context();var started=service.start(c.owner.getId(),c.project.getId(),c.brief.getId(),c.boundary.getId());var claim=tasks.claim(started.jobId(),"concept-one",Duration.ZERO,Duration.ofMinutes(2));tasks.startExecution(started.jobId(),claim.taskAttemptId(),claim.claimToken());assertThat(tasks.recoverExpired(Duration.ZERO,List.of(TaskType.CONCEPT_EXPLORATION))).isEqualTo(1);var recovered=tasks.claimNext(TaskType.CONCEPT_EXPLORATION,"concept-two",Duration.ofMinutes(1),Duration.ofMinutes(2));assertThat(recovered).isNotNull();tasks.startExecution(recovered.taskRunId(),recovered.taskAttemptId(),recovered.claimToken());tasks.fail(recovered.taskRunId(),recovered.taskAttemptId(),recovered.claimToken(),"EXECUTION_FAILED","PERMANENT_EXECUTION_FAILURE",false);OpportunityBriefVersion next=briefs.createDraft(c.owner.getId(),c.project.getId(),c.conversationId,c.brief.getId(),"{\"problem\":\"new\"}",List.of());briefs.confirm(c.owner.getId(),c.project.getId(),next.getId());assertThat(service.current(c.owner.getId(),c.project.getId()).batch().status()).isEqualTo("STALE");}
 @Test void projectIsolationRejectsBatchRead(){Context one=context();Context two=context();var started=service.start(one.owner.getId(),one.project.getId(),one.brief.getId(),one.boundary.getId());assertThatThrownBy(()->service.batch(two.owner.getId(),one.project.getId(),started.batchId())).isInstanceOf(BusinessException.class);tasks.cancel(one.owner.getId(),one.project.getId(),started.jobId());}
 private Context context(){String suffix=UUID.randomUUID().toString();User owner=users.saveAndFlush(User.create("concept-pg-"+suffix+"@example.com","hash","owner"));Project project=projects.saveAndFlush(Project.create(owner,"concept-pg",null,"AI"));var conversation=conversations.create(owner.getId(),project.getId(),null);OpportunityBriefVersion brief=briefs.createDraft(owner.getId(),project.getId(),conversation.getId(),null,"{\"problem\":\"waste\"}",List.of(new OpportunityBriefService.FieldInput("problem","\"waste\"",FieldDecisionStatus.PREFERRED,FieldSourceType.USER_CONFIRMED,"user")));briefs.confirm(owner.getId(),project.getId(),brief.getId());RegulatoryBoundaryRun run=boundaryFoundation.createRun(owner.getId(),project.getId(),brief.getId(),null);boundaryFoundation.start(owner.getId(),project.getId(),run.getId());boundaryFoundation.succeed(owner.getId(),project.getId(),run.getId());RegulatoryBoundaryVersion boundary=boundaryFoundation.createVersion(owner.getId(),project.getId(),run.getId(),RegulatoryBoundaryVersion.Status.READY,"{\"status\":\"READY\"}");return new Context(owner,project,conversation.getId(),brief,boundary);}
 record Context(User owner,Project project,Long conversationId,OpportunityBriefVersion brief,RegulatoryBoundaryVersion boundary){}
}
