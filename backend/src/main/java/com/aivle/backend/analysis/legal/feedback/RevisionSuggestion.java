package com.aivle.backend.analysis.legal.feedback;

import com.aivle.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "revision_suggestions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevisionSuggestion extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_request_id", nullable = false) private RevisionRequest revisionRequest;
    @Column(nullable = false, length = 10) private String label;
    @Column(name = "new_text", nullable = false, columnDefinition = "TEXT") private String newText;
    @Column(nullable = false) private Integer displayOrder;

    public static RevisionSuggestion create(
        RevisionRequest request, String label, String newText, int displayOrder
    ) {
        RevisionSuggestion suggestion = new RevisionSuggestion();
        suggestion.revisionRequest = request;
        suggestion.label = label;
        suggestion.newText = newText;
        suggestion.displayOrder = displayOrder;
        return suggestion;
    }
}
