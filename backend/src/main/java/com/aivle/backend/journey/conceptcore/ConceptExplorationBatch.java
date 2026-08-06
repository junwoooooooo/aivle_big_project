package com.aivle.backend.journey.conceptcore;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.journey.boundary.RegulatoryBoundaryVersion;
import com.aivle.backend.journey.brief.OpportunityBriefVersion;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.taskrun.domain.TaskRun;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name="concept_exploration_batches") @Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ConceptExplorationBatch extends BaseEntity {
    public enum Status { QUEUED, GENERATING, VALIDATING, REPLACING, COMPLETED, NEEDS_INPUT, FAILED, STALE }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id",nullable=false) private Project project;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="brief_version_id",nullable=false) private OpportunityBriefVersion briefVersion;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="boundary_version_id",nullable=false) private RegulatoryBoundaryVersion boundaryVersion;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="task_run_id",nullable=false) private TaskRun taskRun;
    @Column(nullable=false,length=71) private String briefSnapshotHash;
    @Column(nullable=false,length=71) private String boundarySnapshotHash;
    @Column(nullable=false,length=71) private String inputSnapshotHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Status status;
    @Column(nullable=false) private int eligibleCount;
    @Column(nullable=false) private int inspectedCandidates;
    @Column(nullable=false) private int currentRound;
    @Column(nullable=false) private int maxReplacementRounds;
    @Column(nullable=false) private int maxInspectedCandidates;
    @Column(length=100) private String failureCode;
    @Column(nullable=false,columnDefinition="TEXT") private String needsInputJson;
    private LocalDateTime completedAt;
    private LocalDateTime staleAt;

    public static ConceptExplorationBatch queued(Project project, OpportunityBriefVersion brief,
            RegulatoryBoundaryVersion boundary, TaskRun task, String inputHash) {
        if (!project.getId().equals(brief.getProject().getId()) || !project.getId().equals(boundary.getProject().getId()))
            throw new IllegalArgumentException("concept input project mismatch");
        ConceptExplorationBatch value=new ConceptExplorationBatch(); value.project=project; value.briefVersion=brief;
        value.boundaryVersion=boundary; value.taskRun=task; value.briefSnapshotHash=brief.getSnapshotHash();
        value.boundarySnapshotHash=boundary.getSnapshotHash(); value.inputSnapshotHash=inputHash; value.status=Status.QUEUED;
        value.maxReplacementRounds=2; value.maxInspectedCandidates=9; value.needsInputJson="[]"; return value;
    }
    public void generating(){ require(Status.QUEUED); status=Status.GENERATING; }
    public void validating(int inspected){ if(status!=Status.GENERATING&&status!=Status.REPLACING)throw new IllegalStateException();status=Status.VALIDATING;inspectedCandidates=inspected; }
    public void replacing(int round){ if(status!=Status.VALIDATING&&status!=Status.GENERATING)throw new IllegalStateException();status=Status.REPLACING;currentRound=round; }
    public void complete(int eligible){ if(eligible!=3)throw new IllegalArgumentException("exactly three concepts required");status=Status.COMPLETED;eligibleCount=eligible;completedAt=LocalDateTime.now(); }
    public void needsInput(String json){status=Status.NEEDS_INPUT;needsInputJson=json;completedAt=LocalDateTime.now();}
    public void fail(String code){status=Status.FAILED;failureCode=code;completedAt=LocalDateTime.now();}
    public void requeue(){if(status!=Status.FAILED&&status!=Status.GENERATING)throw new IllegalStateException();status=Status.QUEUED;failureCode=null;}
    public void stale(){if(status!=Status.STALE){status=Status.STALE;staleAt=LocalDateTime.now();}}
    public boolean matches(OpportunityBriefVersion brief, RegulatoryBoundaryVersion boundary){return brief.getId().equals(briefVersion.getId())&&brief.getSnapshotHash().equals(briefSnapshotHash)&&boundary.getId().equals(boundaryVersion.getId())&&boundary.getSnapshotHash().equals(boundarySnapshotHash);}
    private void require(Status expected){if(status!=expected)throw new IllegalStateException("invalid batch transition");}
}
