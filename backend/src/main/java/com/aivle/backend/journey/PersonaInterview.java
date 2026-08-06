package com.aivle.backend.journey;
import com.aivle.backend.common.entity.BaseEntity; import com.aivle.backend.project.entity.Project; import com.aivle.backend.taskrun.domain.TaskRun; import jakarta.persistence.*; import java.time.LocalDateTime; import lombok.*;
@Entity @Table(name="persona_interviews") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PersonaInterview extends BaseEntity {
    public enum State { PENDING, RUNNING, SUCCEEDED, FAILED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id",nullable=false) private Project project;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="concept_version_id",nullable=false) private ConceptVersion conceptVersion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="study_id",nullable=false) private PersonaStudy study;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="persona_card_version_id",nullable=false) private PersonaCardVersion personaCardVersion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="task_run_id") private TaskRun taskRun;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private State state;
    @Column(name="result_json",columnDefinition="TEXT") private String resultJson; @Column(length=100) private String error; private LocalDateTime completedAt;
    public static PersonaInterview pending(Project p,ConceptVersion concept,PersonaStudy study,PersonaCardVersion persona){PersonaInterview v=new PersonaInterview();v.project=p;v.conceptVersion=concept;v.study=study;v.personaCardVersion=persona;v.state=State.PENDING;return v;}
    public void start(TaskRun task){taskRun=task;state=State.RUNNING;resultJson=null;error=null;completedAt=null;}
    public void succeed(String result){resultJson=result;state=State.SUCCEEDED;error=null;completedAt=LocalDateTime.now();}
    public void fail(String reason){state=State.FAILED;error=reason;completedAt=LocalDateTime.now();}
}
