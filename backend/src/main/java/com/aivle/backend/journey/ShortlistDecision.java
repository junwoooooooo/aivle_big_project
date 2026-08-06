package com.aivle.backend.journey;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "shortlist_decisions") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShortlistDecision extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id", nullable = false) private Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "idea_version_id", nullable = false) private IdeaVersion ideaVersion;
    @Column(name = "selected_concept_version_ids_json", nullable = false, columnDefinition = "TEXT") private String selectedConceptVersionIdsJson;
    @Column(columnDefinition = "TEXT") private String reason;
    public static ShortlistDecision create(Project p, IdeaVersion idea, String ids, String reason) {
        ShortlistDecision v = new ShortlistDecision(); v.project=p; v.ideaVersion=idea; v.selectedConceptVersionIdsJson=ids; v.reason=reason; return v;
    }
}
