package com.aivle.backend.validation.market;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.user.entity.User;
import com.aivle.backend.validation.PersonaValidationTypes.ValidationStatus;
import com.aivle.backend.validation.panel.PanelInterview;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "market_response_predictions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketResponsePrediction extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "panel_interview_id")
    private PanelInterview panelInterview;
    @Column(nullable = false, length = 200)
    private String title;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ValidationStatus status;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String personaIdsJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String messageVariantsJson;
    @Column(length = 300)
    private String priceContext;
    @Column(length = 80)
    private String primaryChannel;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String resultJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summaryJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String sourceSnapshotJson;
    private LocalDateTime completedAt;

    public static MarketResponsePrediction create(
        Project project,
        User actor,
        PanelInterview interview,
        String title,
        String personaIdsJson,
        String messageVariantsJson,
        String priceContext,
        String primaryChannel
    ) {
        MarketResponsePrediction value = new MarketResponsePrediction();
        value.project = project;
        value.createdBy = actor;
        value.panelInterview = interview;
        value.title = title;
        value.status = ValidationStatus.DRAFT;
        value.personaIdsJson = personaIdsJson;
        value.messageVariantsJson = messageVariantsJson;
        value.priceContext = priceContext;
        value.primaryChannel = primaryChannel;
        value.resultJson = "[]";
        value.summaryJson = "{}";
        value.sourceSnapshotJson = "{}";
        return value;
    }

    public void updateDraft(
        PanelInterview interview,
        String title,
        String personaIdsJson,
        String messageVariantsJson,
        String priceContext,
        String primaryChannel
    ) {
        this.panelInterview = interview;
        this.title = title;
        this.personaIdsJson = personaIdsJson;
        this.messageVariantsJson = messageVariantsJson;
        this.priceContext = priceContext;
        this.primaryChannel = primaryChannel;
        this.status = ValidationStatus.DRAFT;
        this.resultJson = "[]";
        this.summaryJson = "{}";
        this.sourceSnapshotJson = "{}";
        this.completedAt = null;
    }

    public void complete(
        String resultJson,
        String summaryJson,
        String sourceSnapshotJson,
        LocalDateTime now
    ) {
        this.status = ValidationStatus.COMPLETED;
        this.resultJson = resultJson;
        this.summaryJson = summaryJson;
        this.sourceSnapshotJson = sourceSnapshotJson;
        this.completedAt = now;
    }
}
