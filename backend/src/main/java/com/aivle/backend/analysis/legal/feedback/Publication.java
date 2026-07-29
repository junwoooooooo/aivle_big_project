package com.aivle.backend.analysis.legal.feedback;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.document.entity.StructuredPlan;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** 발행 시점의 스냅샷. 이후 기획서가 수정되어도 이 행은 발행 당시 기준으로 보존된다. */
@Entity
@Table(
    name = "publications",
    uniqueConstraints = @UniqueConstraint(name = "uk_publication_cycle", columnNames = {"review_cycle_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Publication extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_cycle_id", nullable = false) private ReviewCycle reviewCycle;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "final_plan_id", nullable = false) private StructuredPlan finalPlan;
    @Column(nullable = false) private Integer finalVersionNumber;
    @Column(nullable = false, columnDefinition = "TEXT") private String snapshotJson;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "published_by_user_id", nullable = false) private User publishedBy;
    @Column(nullable = false) private LocalDateTime publishedAt;

    public static Publication create(
        Project project, ReviewCycle cycle, StructuredPlan finalPlan,
        String snapshotJson, User publishedBy, LocalDateTime publishedAt
    ) {
        Publication publication = new Publication();
        publication.project = project;
        publication.reviewCycle = cycle;
        publication.finalPlan = finalPlan;
        publication.finalVersionNumber = finalPlan.getVersionNumber();
        publication.snapshotJson = snapshotJson;
        publication.publishedBy = publishedBy;
        publication.publishedAt = publishedAt;
        return publication;
    }
}
