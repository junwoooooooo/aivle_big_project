package com.aivle.backend.pipeline.techops.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tech_ops_evidence_references")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TechOpsEvidenceReference extends BaseEntity {
    @Id @Column(length = 64) private String id;
    @Column(name = "preparation_id", nullable = false, length = 64) private String preparationId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "evidence_type", nullable = false, length = 30) private String evidenceType;
    @Column(name = "display_name", nullable = false, length = 255) private String displayName;
    @Column(name = "artifact_id", length = 64) private String artifactId;
    @Column(name = "artifact_ref", length = 1000) private String artifactRef;
    @Column(length = 1000) private String description;
    @Column(name = "provided_by_user_id", nullable = false) private Long providedByUserId;

    public static TechOpsEvidenceReference create(String id, String preparationId, Long projectId, String type,
            String displayName, String artifactId, String description, Long userId) {
        if (blank(id) || blank(preparationId) || projectId == null || blank(type) || blank(displayName)
                || blank(artifactId) || userId == null) throw new IllegalArgumentException("기술·운영 근거 자료가 올바르지 않습니다.");
        TechOpsEvidenceReference value = new TechOpsEvidenceReference();
        value.id=id; value.preparationId=preparationId; value.projectId=projectId; value.evidenceType=type;
        value.displayName=displayName.strip(); value.artifactId=artifactId.strip(); value.artifactRef=null;
        value.description=description == null ? null : description.strip(); value.providedByUserId=userId;
        return value;
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
