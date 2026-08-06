package com.aivle.backend.marketing.content;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.persona.catalog.entity.BaselinePersona;
import com.aivle.backend.project.entity.Project;
import com.aivle.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static com.aivle.backend.marketing.content.MarketingContentTypes.*;

@Entity
@Table(name = "marketing_contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketingContent extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;
    @Column(nullable = false, length = 200)
    private String title;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private Purpose purpose;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Channel channel;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private Format format;
    @Column(nullable = false) private int width;
    @Column(nullable = false) private int height;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_persona_id")
    private BaselinePersona selectedPersona;
    @Column(length = 100) private String recommendedPersonaCode;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String sourceSnapshotJson;
    @Column(name = "panel_interview_id")
    private Long panelInterviewId;
    @Column(name = "market_response_id")
    private Long marketResponseId;
    @Column(nullable = false)
    private int sourceSnapshotVersion;
    @Column(nullable = false) private int currentVersion;

    public static MarketingContent create(
        Project project,
        User createdBy,
        String title,
        Purpose purpose,
        Channel channel,
        Format format,
        int width,
        int height,
        BaselinePersona persona,
        String recommendedPersonaCode,
        String sourceSnapshotJson,
        Long panelInterviewId,
        Long marketResponseId,
        int sourceSnapshotVersion
    ) {
        MarketingContent value = new MarketingContent();
        value.project = project;
        value.createdBy = createdBy;
        value.title = title;
        value.purpose = purpose;
        value.channel = channel;
        value.format = format;
        value.width = width;
        value.height = height;
        value.status = Status.READY;
        value.selectedPersona = persona;
        value.recommendedPersonaCode = recommendedPersonaCode;
        value.sourceSnapshotJson = sourceSnapshotJson;
        value.panelInterviewId = panelInterviewId;
        value.marketResponseId = marketResponseId;
        value.sourceSnapshotVersion = sourceSnapshotVersion;
        value.currentVersion = 1;
        return value;
    }

    public void updateMetadata(
        String title,
        Purpose purpose,
        Channel channel,
        Format format,
        int width,
        int height,
        BaselinePersona persona
    ) {
        this.title = title;
        this.purpose = purpose;
        this.channel = channel;
        this.format = format;
        this.width = width;
        this.height = height;
        this.selectedPersona = persona;
    }

    public int advanceVersion() {
        return ++currentVersion;
    }

    public int refreshSource(
        String snapshotJson,
        Long panelInterviewId,
        Long marketResponseId
    ) {
        this.sourceSnapshotJson = snapshotJson;
        this.panelInterviewId = panelInterviewId;
        this.marketResponseId = marketResponseId;
        return ++sourceSnapshotVersion;
    }
}
