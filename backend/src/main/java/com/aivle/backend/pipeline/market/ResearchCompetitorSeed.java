package com.aivle.backend.pipeline.market;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "research_competitor_seeds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResearchCompetitorSeed extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, length = 500) private String reason;
    @Column(name = "operator_name", length = 200) private String operatorName;
    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;

    public static ResearchCompetitorSeed create(String id, Long projectId, int displayOrder,
            String name, String reason, String operatorName, Long userId) {
        if (blank(id) || projectId == null || displayOrder < 1 || blank(name) || blank(reason) || userId == null)
            throw new IllegalArgumentException("경쟁 씨앗 값이 올바르지 않습니다.");
        ResearchCompetitorSeed value = new ResearchCompetitorSeed();
        value.id=id; value.projectId=projectId; value.displayOrder=displayOrder;
        value.name=name; value.reason=reason;
        value.operatorName=operatorName==null||operatorName.isBlank()?null:operatorName;
        value.createdByUserId=userId; return value;
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
