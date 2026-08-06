package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel; import lombok.Getter; import lombok.NoArgsConstructor;

@Entity @Table(name="persona_studies") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PersonaStudy extends BaseEntity {
    public enum State { DRAFT, GENERATING, READY, FAILED }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id",nullable=false) private Project project;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="idea_version_id",nullable=false) private IdeaVersion ideaVersion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="concept_version_id",nullable=false) private ConceptVersion conceptVersion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="generation_task_run_id") private TaskRun generationTaskRun;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private State state;
    @Column(name="result_json",columnDefinition="TEXT") private String resultJson;
    @Column(length=100) private String error;
    @Column(nullable=false,length=300) private String syntheticNotice;
    private LocalDateTime completedAt;
    public static PersonaStudy create(Project p,IdeaVersion idea,ConceptVersion concept){PersonaStudy v=new PersonaStudy();v.project=p;v.ideaVersion=idea;v.conceptVersion=concept;v.state=State.DRAFT;v.syntheticNotice="AI가 생성한 합성 사용자이며 실제 고객 데이터가 아닙니다.";return v;}
    public void start(TaskRun task){generationTaskRun=task;state=State.GENERATING;resultJson=null;error=null;completedAt=null;}
    public void succeed(String result){resultJson=result;state=State.READY;error=null;completedAt=LocalDateTime.now();}
    public void fail(String reason){state=State.FAILED;error=reason;completedAt=LocalDateTime.now();}
}
