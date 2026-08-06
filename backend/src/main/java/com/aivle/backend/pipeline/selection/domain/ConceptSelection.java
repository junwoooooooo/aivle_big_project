package com.aivle.backend.pipeline.selection.domain;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concept_selections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConceptSelection extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "concept_id", nullable = false, length = 64) private String conceptId;
    @Column(name = "selection_reason", nullable = false, length = 2000) private String selectionReason;
    @Column(name = "request_hash", nullable = false, length = 71) private String requestHash;
    @Column(name = "selected_by_user_id", nullable = false) private Long selectedByUserId;
    @Column(name = "selected_at", nullable = false) private Instant selectedAt;
    @Column(name = "is_current", nullable = false) private boolean currentSelection;

    public static ConceptSelection select(Long projectId, String conceptId, String reason, String requestHash, Long userId, Instant selectedAt) {
        if (projectId == null || conceptId == null || conceptId.isBlank()) throw new IllegalArgumentException("project and concept are required");
        if (reason == null || reason.isBlank() || reason.length() > 2000) throw new IllegalArgumentException("selection reason is required");
        if (requestHash == null || !requestHash.matches("sha256:[0-9a-f]{64}")) throw new IllegalArgumentException("request hash is invalid");
        ConceptSelection value = new ConceptSelection();
        value.projectId = projectId;
        value.conceptId = conceptId;
        value.selectionReason = reason.strip();
        value.requestHash = requestHash;
        value.selectedByUserId = userId;
        value.selectedAt = selectedAt;
        value.currentSelection = true;
        return value;
    }

    public void supersede() { currentSelection = false; }
}
